package redis4s

import cats.MonadError
import redis4s.CommandCodec.Aux
import redis4s.algebra._
import redis4s.ops.RedisOps

trait RedisClient[F[_]]
    extends StringCommands[F]
    with GenericCommands[F]
    with StreamCommands[F]
    with ConnectionCommands[F]
    with ServerCommands[F]

trait RedisPubSubClient[F[_]]

object SimpleClient {
  def wrap[F[_]: MonadError[*[_], Throwable]](conn: Connection[F]): RedisClient[F] =
    new RedisOps[F] {
      override def run[R, P](r: R)(implicit codec: Aux[R, P]): F[P] = Connection.request(r)(conn)
    }
}
