package redis4s

import cats.free._
import redis4s.CommandCodec.Aux

package object free {
  type RedisIO[A] = FreeApplicative[RCommand, A]
}

package free {
  sealed trait RCommand[A]
  object RCommand   {
    final case class C[R, A](r: R, codec: CommandCodec.Aux[R, A]) extends RCommand[A]
  }

  object RedisIO {
    def lift[R, P](command: R)(implicit codec: Aux[R, P]): RedisIO[P] =
      FreeApplicative.lift(RCommand.C(command, codec))

    def pure[A](a: A): RedisIO[A] = FreeApplicative.pure(a)

    val client: RedisClient[RedisIO] = new RedisC[RedisIO] {
      override def run[R, P](r: R)(implicit codec: Aux[R, P]): RedisIO[P] =
        RedisIO.lift(r)(codec)
    }
  }
}
