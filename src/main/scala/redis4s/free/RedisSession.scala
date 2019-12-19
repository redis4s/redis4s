package redis4s.free

import cats.data.{Chain, Const, Tuple2K}
import cats.implicits._
import cats.effect._
import cats.{Applicative, ~>}
import redis4s.{Connection, RedisMessage}

trait RedisSession[F[_]] {
  def run[A](io: RedisIO[A]): F[A]
  def transact[A](io: RedisIO[A], watchKeys: Vector[String] = Vector.empty): F[A]
}

object RedisSession {
  private val logger = org.log4s.getLogger
  logger.info("TODO")

  def apply[F[_]](implicit ev: RedisSession[F]): ev.type = ev

  def create[F[_]: Concurrent](conn: Connection[F]): RedisSession[F] = new RedisSession[F] {
    import FreeIO._

    implicit val applicativeNat: Applicative[Tuple2K[RequestLog[F, *], F, *]] = Tuple2K
      .catsDataApplicativeForTuple2K(
        implicitly[Applicative[Const[Chain[Lift[F, Unit]], *]]],
        implicitly[Applicative[F]]
      )
      .asInstanceOf[Applicative[Tuple2K[RequestLog[F, *], F, *]]]

    val nat: IOOp[F, *] ~> Tuple2K[RequestLog[F, *], F, *] = logNat[F] and ioNat[F]

    override def run[A](a: RedisIO[A]): F[A] = {
      Concurrent[F]
        .delay(a.foldMap(lioNat))
        .map(_.foldMap(nat))
        .flatMap {
          case Tuple2K(log, fa) =>
            val lifted    = log.getConst.toVector
            val requests  = lifted.map(_.request)
            val callbacks = lifted.map(_.complete)
            if (logger.isTraceEnabled)
              requests.foreach(r => logger.trace(s"-> [P ${requests.size}] ${r.prettyPrint}"))
            conn
              .pipeline(requests)
              .flatMap(
                _.zip(callbacks).traverse_ {
                  case (m, f) =>
                    logger.trace(s"<- [P ${requests.size}] ${m.prettyPrint}")
                    f(m)
                }
              ) >> fa
        }
    }

    override def transact[A](a: RedisIO[A], watchKeys: Vector[String]): F[A] = {
      Concurrent[F]
        .delay(a.foldMap(lioNat))
        .map(_.foldMap(nat))
        .flatMap {
          case Tuple2K(log, fa) =>
            val lifted    = log.getConst.toVector
            val requests  = lifted.map(_.request)
            val callbacks = lifted.map(_.complete)
            if (requests.isEmpty) fa // `callbacks` should be empty too
            else {
              conn
                .transact(watchKeys.map(RedisMessage.string), requests)
                .map(_.zip(callbacks))
                .flatMap {
                  _.traverse_ {
                    case (m, f) => f(m)
                  } >> fa
                }
            }
        }
    }
  }
}
