package redis4s.algebra

import cats.implicits._

import redis4s.RedisMessageDecoder._
import redis4s.CommandCodec._

trait ConnectionCommands[F[_]] {
  def echo(message: String): F[String]
  def ping(message: Option[String] = None): F[ConnectionCommands.Pong]
  def swapDb(index1: Int, index2: Int): F[Unit]
}

object ConnectionCommands {
  case class Echo(message: String)
  case class Ping(message: Option[String])
  case class SwapDb(index1: Int, index2: Int)

  case class Pong(msg: String)
  object Echo {
    implicit val codec: Aux[Echo, String] = mk[Echo, String](e => cmd("ECHO", e.message))(_.asString)
  }

  object Ping {
    implicit val codec: Aux[Ping, Pong] =
      mk[Ping, Pong](p => cmd("PING").append(p.message)) { m => m.asStatus.orElse(m.asString).map(Pong) }
  }

  object SwapDb {
    implicit val codec: Aux[SwapDb, Unit] =
      mk[SwapDb, Unit](s => cmd("SWAPDB", s.index1.toString, s.index2.toString))(_.asStatus.void)
  }
}
