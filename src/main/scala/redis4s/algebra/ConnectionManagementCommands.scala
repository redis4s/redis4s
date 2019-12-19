package redis4s.algebra

import cats.implicits._

import redis4s.RedisMessageDecoder._
import redis4s.CommandCodec._

trait ConnectionManagementCommands[F[_]] {
  def auth(password: String): F[Unit]
  def quit(): F[Unit]
  def select(db: Int): F[Unit]
}

object ConnectionManagementCommands {
  case class Auth(password: String)
  case class Quit()
  case class Select(db: Int)

  object Auth {
    implicit val codec: Aux[Auth, Unit] = mk[Auth, Unit](p => cmd("AUTH", p.password).result)(_.asStatus.void)
  }

  object Quit {
    implicit val codec: Aux[Quit, Unit] = mk[Quit, Unit](_ => cmd("QUITE").result)(_.asStatus.void)
  }

  object Select {
    implicit val codec: Aux[Select, Unit] = mk[Select, Unit](s => cmd("SELECT", s.db.toString).result)(_.asStatus.void)
  }
}
