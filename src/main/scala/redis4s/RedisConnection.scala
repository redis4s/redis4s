package redis4s

import cats._
import cats.data.Chain
import cats.implicits._
import fs2.{Chunk, Stream}
import redis4s.RedisMessageDecoder._
import redis4s.algebra.ConnectionManagementCommands

trait Connection[F[_]] {
  def execute(req: RedisMessage): F[RedisMessage]
  def pipeline(reqs: Vector[RedisMessage]): F[Vector[RedisMessage]]
  def transact(watchKeys: Vector[RedisMessage.Bulk], reqs: Vector[RedisMessage]): F[Vector[RedisMessage]]
}

object Connection {
  private val logger = org.log4s.getLogger

  def request[F[_], R, P](r: R)(conn: Connection[F])(
    implicit M: MonadError[F, Throwable],
    C: CommandCodec.Aux[R, P]
  ): F[P] = {
    val request = C.encodeCommand(r)
    logger.trace(s"-> ${request.prettyPrint}")
    conn.execute(request).flatMap { m =>
      logger.trace(s"<- ${m.prettyPrint}")
      m.checkError.flatMap(C.decodeResponse).liftTo[F]
    }
  }

  def authenticate[F[_]: MonadError[*[_], Throwable]](conn: Connection[F], password: String): F[Unit] =
    request(ConnectionManagementCommands.Auth(password))(conn)

  def select[F[_]: MonadError[*[_], Throwable]](conn: RedisConnection[F], db: Int): F[Unit] =
    request(ConnectionManagementCommands.Select(db))(conn)
}

// sub only
trait PubSubConnection[F[_]] {
  def execute0(req: RedisMessage): F[Unit]
  def stream(): Stream[F, RedisMessage]
}

trait RedisConnection[F[_]] extends Connection[F] with PubSubConnection[F]

object RedisConnection {
  val MULTI: RedisMessage   = RedisMessage.cmd("MULTI")
  val EXEC: RedisMessage    = RedisMessage.cmd("EXEC")
  val DISCARD: RedisMessage = RedisMessage.cmd("DISCARD")
  val UNWATCH: RedisMessage = RedisMessage.cmd("UNWATCH")

  def apply[F[_]: MonadError[*[_], Throwable]](socket: ProtocolSocket[F, RedisMessage]): RedisConnection[F] =
    new RedisConnection[F] {

      override def execute0(req: RedisMessage): F[Unit] = socket.write(req)

      override def execute(req: RedisMessage): F[RedisMessage] = socket.write(req) >> socket.read

      override def pipeline(reqs: Vector[RedisMessage]): F[Vector[RedisMessage]] =
        if (reqs.isEmpty) Vector.empty[RedisMessage].pure[F]
        else socket.writeN(Chain.fromSeq(reqs)) >> socket.readN(reqs.size).map(_.toVector)

      override def transact(
        watchKeys: Vector[RedisMessage.Bulk],
        reqs: Vector[RedisMessage]
      ): F[Vector[RedisMessage]] = {
        def unwatch =
          socket.write(UNWATCH) >>
            socket.read >>=
            (_.checkError.flatMap(_.asStatus).void.liftTo[F])

        def watch =
          if (watchKeys.isEmpty) ().pure[F]
          else {
            val command = RedisMessage.string("WATCH") +: watchKeys
            socket.write(RedisMessage.arr(command)) >>
              socket.read >>=
              (_.checkError.flatMap(_.asStatus).void.liftTo[F])
          }

        for {
          // watch all
          _ <- watch.handleErrorWith(e => unwatch >> e.raiseError[F, Unit])
          // send and receive responses
          _     <- socket.writeN(Chain.fromSeq(reqs).prepend(MULTI).append(EXEC))
          resps <- socket.readN(reqs.size + 2).map(_.toVector)
          // response for MULTI
          _ <- resps.head.checkError.flatMap(_.checkStatus("OK")).leftMap(TransactionError(_)).liftTo[F]
          // response for QUEUED commands
          _ <- resps
                .slice(1, reqs.size + 1)
                .traverse_(_.checkError.flatMap(_.checkStatus("QUEUED")).leftMap(TransactionError(_)).liftTo[F]) // check QUEUED commands
          // response for EXEC
          as <- resps.last.nilOption.fold(TransactionError("Aborted").raiseError[F, Vector[RedisMessage]]) {
                 _.asArrayOfSize(reqs.size).leftMap(TransactionError(_)).liftTo[F]
               }
        } yield as
      }

      override def stream(): Stream[F, RedisMessage] =
        Stream
          .eval(socket.readN(512))
          .repeat
          .map(Chunk.chain)
          .flatMap(Stream.chunk)
    }
}
