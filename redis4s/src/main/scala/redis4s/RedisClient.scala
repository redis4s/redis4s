package redis4s

import cats.MonadError
import redis4s.CommandCodec.Aux

trait RedisClient[F[_]]
    extends StringCommands[F]
    with GenericCommands[F]
    with StreamCommands[F]
    with ConnectionCommands[F]
    with ServerCommands[F]

trait RedisPubSubClient[F[_]]

object SimpleClient {
  def wrap[F[_]: MonadError[*[_], Throwable]](conn: Connection[F]): RedisClient[F] =
    new RedisC[F] {
      override def run[R, P](r: R)(implicit codec: Aux[R, P]): F[P] = Connection.request(r)(conn)
    }
}
