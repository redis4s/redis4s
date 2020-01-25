package redis4s.free

import redis4s.RedisMessage

final case class IOOp[F[_], A](
  request: RedisMessage,
  complete: RedisMessage => F[Unit],
  get: F[A]
)
