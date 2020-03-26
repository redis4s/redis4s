package redis4s.algebra

import cats.implicits._
import redis4s.CommandCodec._
import redis4s.RedisMessageDecoder._
import redis4s.algebra.StringCommands.SetModifier

import scala.concurrent.duration._

trait StringCommands[F[_]] {
  def set(
    key: String,
    value: String,
    expire: FiniteDuration = 0.seconds,
    setModifier: Option[SetModifier] = None
  ): F[Unit]
  def get(key: String): F[Option[String]]
}

object StringCommands {
  case class Set(key: String, value: String, expire: FiniteDuration, modifier: Option[SetModifier])
  case class Get(key: String)

  object Set {
    implicit val codec: Aux[Set, Unit] =
      mk[Set, Unit] { s =>
        cmd("SET", s.key, s.value)
          .append("PX" -> s.expire.toMillis.some.filter(_ > 0))
          .append(s.modifier.map(_.toString))
          .result
      }(_.asStatus.void)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  sealed trait SetModifier {
    override def toString: String = super.toString
  }
  object SetModifier {
    case object NX extends SetModifier
    case object XX extends SetModifier
  }

  object Get {
    implicit val codec: Aux[Get, Option[String]] =
      mk[Get, Option[String]](g => cmd("GET", g.key).result)(
        _.asOption.traverse(_.asString)
      )
  }
}
