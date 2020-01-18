package redis4s.free

import cats.data.{Chain, Const}
import cats.~>
import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.free._
import redis4s.{RedisError, RedisMessage}

object FreeIO {
  type LIO[F[_], A] = FreeApplicative[IOOp[F, *], A]
  final case class Lift[F[_], A](
    request: RedisMessage,
    complete: RedisMessage => F[Unit]
  )

  type RequestLog[F[_], A] = Const[Chain[Lift[F, A]], A]

  def lioNat[F[_]: Concurrent]: RequestOp ~> LIO[F, *] = new (RequestOp ~> LIO[F, *]) {
    override def apply[A](fa: RequestOp[A]): LIO[F, A] = {
      fa match {
        case RequestOp.Req(r, codec) =>
          val deferred = Deferred.unsafe[F, Either[RedisError, A]]

          val op: IOOp[F, A] = IOOp[F, A](
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

  def logNat[F[_]]: IOOp[F, *] ~> RequestLog[F, *] = new (IOOp[F, *] ~> RequestLog[F, *]) {
    override def apply[A](fa: IOOp[F, A]): RequestLog[F, A] = Const(Chain(Lift(fa.request, fa.complete)))
  }

  def ioNat[F[_]]: IOOp[F, *] ~> F = new (IOOp[F, *] ~> F) {
    override def apply[A](fa: IOOp[F, A]): F[A] = fa.get
  }
}
