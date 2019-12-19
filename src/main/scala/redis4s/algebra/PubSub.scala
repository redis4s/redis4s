package redis4s.algebra

import cats.implicits._
import fs2.Stream
import redis4s.{DecodeError, InvalidOperation, RedisMessage}
import redis4s.CommandCodec._
import redis4s.RedisMessageDecoder._

import scala.collection.immutable.Seq

trait PubSub[F[_]] {
  def publish(channel: String, message: String): F[Long]
  def pChannels(pattern: Option[String]): F[Seq[String]]
  def pNumSub(channels: Seq[String]): F[Seq[Long]]
  def pNumPat(): F[Long]
}

// we have separate interface for consuming,
// because once a connection subscribe something it
// no longer behave like a normal redis connection.
trait SubStream[F[_]] {
  def subscribe(channel: String, channels: String*): F[Unit]
  def psubscribe(pattern: String, patterns: String*): F[Unit]
  def unsubscribe(channel: String, channels: String*): F[Unit]
  def punsubscribe(pattern: String, patterns: String*): F[Unit]
  def stream(): Stream[F, PubSub.Message]
}

object PubSub {
  case class Publish(channel: String, message: String)
  case class Subscribe(channel: String, channels: Seq[String])
  case class PSubscribe(pattern: String, patterns: Seq[String])
  case class Unsubscribe(channel: String, channels: Seq[String])
  case class PUnsubscribe(pattern: String, patterns: Seq[String])

  // PUBSUB <SUBCOMMAND>
  case class PChannels(pattern: Option[String])
  case class PNumSub(channels: Seq[String])
  case class PNumPat()

  case class Message(channel: String, message: String)
  object Message {
    def decode(a: RedisMessage): Either[DecodeError, Message] = {
      a.asArrayOfSize(2).flatMap {
        case Vector(ch, msg) =>
          (ch.asString, msg.asString).mapN(Message.apply)
      }
    }
  }

  object Publish {
    implicit val codec: Aux[Publish, Long] =
      mk[Publish, Long](p => cmd("PUBLISH", p.channel, p.message).result)(_.asInteger)
  }

  object PChannels {
    implicit val codec: Aux[PChannels, Seq[String]] =
      mk[PChannels, Seq[String]](p => cmd("PUBSUB", "CHANNELS").append(p.pattern).result)(
        _.asArray.flatMap(_.traverse(_.asString))
      )
  }

  object PNumSub {
    implicit val codec: Aux[PNumSub, Long] =
      mk[PNumSub, Long](p => cmd("PUBSUB", "NUMSUB").append(p.channels).result)(_.asInteger)
  }

  object PNumPat {
    implicit val codec: Aux[PNumPat, Long] =
      mk[PNumPat, Long](_ => cmd("PUBSUB", "NUMPAT").result)(_.asInteger)
  }

  object Subscribe {
    implicit val codec: Aux[Subscribe, Nothing] =
      mk[Subscribe, Nothing](s => cmd("SUBSCRIBE", s.channel).append(s.channels).result)(_ =>
        throw InvalidOperation("Subscribe command has no response")
      )
  }

  object PSubscribe {
    implicit val codec: Aux[PSubscribe, Nothing] =
      mk[PSubscribe, Nothing](s => cmd("PSUBSCRIBE", s.pattern).append(s.patterns).result)(_ =>
        throw InvalidOperation("PSubscribe command has no response")
      )
  }

  object Unsubscribe {
    implicit val codec: Aux[Unsubscribe, Nothing] =
      mk[Unsubscribe, Nothing](s => cmd("UNSUBSCRIBE", s.channel).append(s.channels).result)(_ =>
        throw InvalidOperation("Unsubscribe command has no response")
      )
  }

  object PUnsubscribe {
    implicit val codec: Aux[PUnsubscribe, Nothing] =
      mk[PUnsubscribe, Nothing](s => cmd("PUNSUBSCRIBE", s.pattern).append(s.patterns).result)(_ =>
        throw InvalidOperation("Subscribe command has no response")
      )
  }
}
