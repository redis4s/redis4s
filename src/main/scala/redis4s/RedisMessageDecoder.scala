package redis4s

import cats.Show
import cats.implicits._
import scodec.bits.ByteVector
import redis4s.internal.ops.byteVectorOps

object RedisMessageDecoder {
  implicit class redisMessageOps(private val message: RedisMessage) extends AnyVal {
    import RedisMessage._

    def matchType[A](expected: String)(pf: PartialFunction[RedisMessage, A]): Either[DecodeError, A] = {
      pf.andThen(_.asRight[DecodeError])
        .applyOrElse(message, (_: RedisMessage) => DecodeError.wrongType(expected, message).asLeft)
    }

    def checkStatus(status: String): Either[DecodeError, Unit] =
      asStatus.flatMap {
        case x if x == status => ().asRight
        case x                => DecodeError.general(s"Status error, '$status' expected, got '$x'").asLeft
      }

    def checkError: Either[ErrorResponse, RedisMessage] = message match {
      case Error(message) => ErrorResponse(message).asLeft
      case a              => a.asRight
    }

    def asStatus: Either[DecodeError, String] =
      matchType("Status") { case Status(message) => message }

    def asError: Either[DecodeError, String] =
      matchType("Error") { case Error(err) => err }

    def asInteger: Either[DecodeError, Long] =
      matchType("Integer") { case Integer(id) => id }

    def asBytes: Either[DecodeError, ByteVector] =
      matchType("NonNull Bulk") { case Bulk(Some(value)) => value }

    def asNull: Either[DecodeError, Unit] =
      matchType("Empty Bulk") { case Bulk(None) => () }

    def asNil: Either[DecodeError, Unit] =
      matchType("Empty Arr") { case Arr(None) => () }

    def isNil: Boolean = {
      message match {
        case Arr(None) => true
        case _         => false
      }
    }

    // redis often uses Nil as NULL
    def nilOption: Option[RedisMessage] = message match {
      case Arr(None) => none
      case a         => a.some
    }

    def asString: Either[DecodeError, String] =
      asBytes.flatMap(_.utf8)

    // sometimes we need to treat Nil as emtpy Arr
    def nilAsEmpty: RedisMessage = message match {
      case Arr(None) => arr(Vector.empty)
      case a         => a
    }

    def asArray: Either[DecodeError, Vector[RedisMessage]] =
      matchType("NonEmptyArr") { case Arr(Some(as)) => as }

    def asArrayOfSize(n: Int): Either[DecodeError, Vector[RedisMessage]] =
      asArray.flatMap {
        case xs if xs.length == n => xs.asRight
        case xs                   => DecodeError.general(s"WrongType expect Array(len=$n), got Array(len=${xs.length})").asLeft
      }

    def asPairs: Either[DecodeError, Vector[(RedisMessage, RedisMessage)]] =
      asArray.flatMap { msgs =>
        val size = msgs.size
        if (size % 2 == 0) {
          msgs
            .sliding(2, 2)
            .map { case Vector(a, b) => a -> b }
            .toVector
            .asRight
        } else {
          DecodeError.general(s"Cannot convert Arr of size $size to pairs").asLeft
        }
      }

    def asMap: Either[DecodeError, Map[String, RedisMessage]] =
      asPairs.flatMap(
        _.traverse {
          case (a, b) => a.asString.map(_ -> b)
        }.map(_.toMap)
      )

    def asStringMap: Either[DecodeError, Map[String, String]] =
      asPairs.flatMap(
        _.traverse {
          case (a, b) => (a.asString, b.asString).tupled
        }.map(_.toMap)
      )
  }

  implicit class mapOps[A, B](private val m: Map[A, B]) extends AnyVal {
    def access(key: A)(implicit show: Show[A]): Either[DecodeError, B] = {
      m.get(key).liftTo[Either[DecodeError, *]](DecodeError.general(s"Missing key '${show.show(key)}'"))
    }
  }
}
