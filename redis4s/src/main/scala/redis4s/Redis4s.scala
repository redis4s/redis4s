package redis4s

import java.net.InetSocketAddress

import cats.implicits._
import cats.effect._
import fs2.io.tcp._
import fs2.io.tls.{TLSContext, TLSParameters}
import io.chrisdavenport.keypool.KeyPool
import redis4s.Pool.{RequestKey, use}
import redis4s.free.RedisSession

import scala.concurrent.duration._

case class Redis4sTLSConfig(
  context: TLSContext,
  params: TLSParameters
)

case class Redis4sConfig[F[_]](
  host: String,
  port: Int,
  db: Int,
  auth: Option[String],
  socketGroup: Resource[F, SocketGroup],
  socketTimeout: FiniteDuration,
  tlsConfig: Option[Resource[F, Redis4sTLSConfig]],
  readChunkSizeInBytes: Int,
  maxResponseSizeInBytes: Int
)

object Redis4sConfig {
  def default[F[_]: Sync: ContextShift]: Redis4sConfig[F] =
    Redis4sConfig[F](
      host = "localhost",
      port = 6379,
      db = 0,
      auth = none,
      socketGroup = newSocketGroup[F],
      socketTimeout = 3.seconds,
      tlsConfig = none,
      readChunkSizeInBytes = 1024 * 1024,
      maxResponseSizeInBytes = 10 * 1024 * 1024
    )

  def defaultWithInsecureTLS[F[_]: Sync: ContextShift](blocker: Blocker): Redis4sConfig[F] =
    default[F].copy(tlsConfig = insecureTLSConfig[F](blocker).some)

  def insecureTLSConfig[F[_]: Sync: ContextShift](blocker: Blocker): Resource[F, Redis4sTLSConfig] =
    Resource
      .liftF(TLSContext.insecure[F](blocker))
      .map(ctx => Redis4sTLSConfig(ctx, TLSParameters.Default))

  def newSocketGroup[F[_]: Sync: ContextShift]: Resource[F, SocketGroup] = {
    for {
      b  <- Blocker[F]
      sg <- SocketGroup[F](b)
    } yield sg
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply[F[_]: Sync: ContextShift](host: String, port: Int): Redis4sConfig[F] =
    default[F].copy(host = host, port = port)
}

case class Redis4sPoolConfig(
  size: Int,
  maxIdleTime: FiniteDuration
)

object Redis4sPoolConfig {
  def default: Redis4sPoolConfig = Redis4sPoolConfig(20, 1.minute)
}

object Redis4s {

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, RedisSession[F]] =
    apply[F](Redis4sConfig.default[F], Redis4sPoolConfig.default)

  def apply[F[_]: ConcurrentEffect: ContextShift: Timer](
    config: Redis4sConfig[F],
    poolConfig: Redis4sPoolConfig
  ): Resource[F, RedisSession[F]] = {
    Pool
      .fromResource(connection(config), poolConfig)
      .map(fromPool(_))
      .map(RedisSession.create(_))
  }

  def fromPool[F[_]: Bracket[*[_], Throwable]](
    clientPool: KeyPool[F, RequestKey, RedisConnection[F]]
  ): Connection[F] =
    new Connection[F] {
      override def execute(req: RedisMessage): F[RedisMessage]                                                         = use(clientPool)(_.execute(req))
      override def pipeline(reqs: Vector[RedisMessage]): F[Vector[RedisMessage]]                                       = use(clientPool)(_.pipeline(reqs))
      override def transact(watchKeys: Vector[RedisMessage.Bulk], reqs: Vector[RedisMessage]): F[Vector[RedisMessage]] =
        use(clientPool)(_.transact(watchKeys, reqs))
    }

  def connection[F[_]: ConcurrentEffect: ContextShift: Timer](
    rc: Redis4sConfig[F]
  ): Resource[F, RedisConnection[F]] = {
    val connectTls = (socket: Socket[F]) => {
      rc.tlsConfig.fold(Resource.liftF(socket.pure[F])) {
        _.flatMap {
          case Redis4sTLSConfig(context, params) =>
            context.client(socket, params)
        }
      }
    }
    for {
      sg <- rc.socketGroup
      addr       = new InetSocketAddress(rc.host, rc.port)
      rawSocket <- sg.client(
                     addr,
                     noDelay = true,
                     sendBufferSize = rc.readChunkSizeInBytes,
                     receiveBufferSize = rc.readChunkSizeInBytes
                   )
      socket    <- connectTls(rawSocket)
      bvs        = BitVectorSocket.wrap(socket, rc.socketTimeout)
      ps         = ProtocolSocket.wrap[F, RedisMessage](bvs, rc.readChunkSizeInBytes, rc.maxResponseSizeInBytes)
      c          = RedisConnection(ps)
      _         <- Resource.liftF(rc.auth.traverse(Connection.authenticate(c, _)))
      _         <- Resource.liftF(Connection.select(c, rc.db))
    } yield c
  }

  def simple[F[_]: ConcurrentEffect](connection: Connection[F]): RedisClient[F] = SimpleClient.wrap(connection)

}
