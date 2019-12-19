package redis4s

import cats.MonadError
import redis4s.CommandCodec.Aux
import redis4s.algebra._
import redis4s.ops.{ConnectionOps, KeyOps, RunOps, ServerOps, StreamOps, StringOps}

trait RedisClient[F[_]]
    extends StringCommands[F]
    with KeyCommands[F]
    with StreamCommands[F]
    with ConnectionCommands[F]
    with ServerCommands[F]

trait RedisPubSubClient[F[_]]

object SimpleClient {
  def wrap[F[_]: MonadError[*[_], Throwable]](conn: Connection[F]): RedisClient[F] =
    new RedisClient[F]
      with StringOps[F]
      with KeyOps[F]
      with StreamOps[F]
      with ConnectionOps[F]
      with RunOps[F]
      with ServerOps[F] {
      override def run[R, P](r: R)(implicit codec: Aux[R, P]): F[P] = Connection.request(r)(conn)
    }
}
