package redis4s.algebra

import cats.implicits._

import redis4s.RedisMessageDecoder._
import redis4s.CommandCodec._

trait ServerCommands[F[_]] {
  def flushAll(async: Boolean): F[Unit]
  def flushDB(async: Boolean): F[Unit]
}

object ServerCommands {
  case class FlushAll(async: Boolean)
  case class FlushDB(async: Boolean)

  object FlushAll {
    implicit val codec: Aux[FlushAll, Unit] = mk[FlushAll, Unit] { f =>
      cmd("FLUSHALL").append("ASYNC".some.filter(_ => f.async)).result
    }(_.asStatus.as(()))
  }

  object FlushDB {
    implicit val codec: Aux[FlushDB, Unit] = mk[FlushDB, Unit] { f =>
      cmd("FLUSHDB").append("ASYNC".some.filter(_ => f.async)).result
    }(_.asStatus.as(()))
  }
}
