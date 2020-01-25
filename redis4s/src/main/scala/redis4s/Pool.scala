package redis4s

import cats.implicits._
import cats.effect._
import io.chrisdavenport.keypool.{KeyPool, KeyPoolBuilder, Reusable}

object Pool {
  def fromResource[F[_]: ConcurrentEffect: Timer](
    client: Resource[F, RedisConnection[F]],
    pc: Redis4sPoolConfig
  ): Resource[F, KeyPool[F, RequestKey, RedisConnection[F]]] = {
    val pool = KeyPoolBuilder[F, RequestKey, (RedisConnection[F], F[Unit])](
      _ => client.allocated,
      _._2
    ).withIdleTimeAllowedInPool(pc.maxIdleTime)
      .withMaxPerKey(_ => pc.size)
      .withMaxTotal(pc.size)
      .withDefaultReuseState(Reusable.Reuse)
      .build

    pool.map(_.map(_._1))
  }

  trait RequestKey

  object RequestKey {
    object Generic extends RequestKey
  }

  def use[F[_]: Bracket[*[_], Throwable], A](
    pool: KeyPool[F, RequestKey, RedisConnection[F]],
    key: RequestKey = RequestKey.Generic
  )(fa: RedisConnection[F] => F[A]): F[A] = {
    pool.take(key).use { m =>
      fa(m.value).attempt
        .flatMap {
          _.fold(e => m.canBeReused.set(Reusable.DontReuse) >> e.raiseError[F, A], _.pure[F])
        }
    }
  }
}
