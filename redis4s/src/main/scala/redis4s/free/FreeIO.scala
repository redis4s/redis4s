package redis4s.free

import cats.data.{Chain, Const}
import cats.~>
import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.free._
import redis4s.{RedisError, RedisMessage}

final case class RCmd[F[_], A](
  request: RedisMessage,
  complete: RedisMessage => F[Unit],
  get: F[A]
)

object FreeIO {
  type RIO[F[_], A] = FreeApplicative[RCmd[F, *], A]
  final case class Lift[F[_]](
    request: RedisMessage,
    complete: RedisMessage => F[Unit]
  )

  type RCollect[F[_], A] = Const[Chain[Lift[F]], A]

  def lioNat[F[_]: Concurrent]: RCommand ~> RIO[F, *] =
    new (RCommand ~> RIO[F, *]) {
      override def apply[A](fa: RCommand[A]): RIO[F, A] = {
        fa match {
          case RCommand.C(r, codec) =>
            val deferred = Deferred.unsafe[F, Either[RedisError, A]]

            val op: RCmd[F, A] = RCmd[F, A](
              codec.encodeCommand(r),
              (a: RedisMessage) => {
                deferred.complete(codec.decodeResponse(a))
              },
              deferred.get.flatMap(_.liftTo[F])
            )
            FreeApplicative.lift(op)
        }
      }
    }

  def collectNat[F[_]]: RCmd[F, *] ~> RCollect[F, *] =
    new (RCmd[F, *] ~> RCollect[F, *]) {
      override def apply[A](fa: RCmd[F, A]): RCollect[F, A] = Const(Chain(Lift(fa.request, fa.complete)))
    }

  def ioNat[F[_]]: RCmd[F, *] ~> F =
    new (RCmd[F, *] ~> F) {
      override def apply[A](fa: RCmd[F, A]): F[A] = fa.get
    }
}
