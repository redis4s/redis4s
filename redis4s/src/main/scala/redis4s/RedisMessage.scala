package redis4s

import cats.Show
import cats.syntax.all._
import scodec._
import scodec.bits._
import scodec.codecs._
import redis4s.internal.ops.stringByteVectorOps
import redis4s.scodecs._

import scala.util.Try

// REdis Serialization Protocol
sealed trait RedisMessage
object RedisMessage {

  case class Status(message: String)                    extends RedisMessage // "+OK\r\n"
  case class Error(message: String)                     extends RedisMessage // "-Error message\r\n"
  case class Integer(i: Long)                           extends RedisMessage // ":15\r\n"
  case class Bulk(message: Option[ByteVector])          extends RedisMessage // "$6\r\nfoobar\r\n"
  case class Arr(message: Option[Vector[RedisMessage]]) extends RedisMessage // "*0\r\n" "*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"

  def arr(as: Vector[RedisMessage]): Arr                = Arr(as.some)
  def status(str: String): Status                       = Status(str)
  def string(str: String): Bulk                         = Bulk(str.bv.some)
  def buf(bv: ByteVector): Bulk                         = Bulk(bv.some)
  def nil: RedisMessage                                 = Arr(none)
  def `null`: RedisMessage                              = Bulk(none)
  def cmd(command: String, args: String*): RedisMessage = arr((Vector(command) ++ args).map(string))

  implicit def codec: Codec[RedisMessage] = CodecInstance.codec

  implicit val show: Show[RedisMessage] = Show.show[RedisMessage](_.prettyPrint)

  // stack-safety?
  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  implicit class ops(private val r: RedisMessage) {
    final def prettyPrint: String = {
      r match {
        case Bulk(Some(a)) =>
          a.decodeUtf8.fold(_ => s"Binary(${a.size}))", s => s""""$s"""")
        case Bulk(None)      => s"Null"
        case Status(message) => s"Status($message)"
        case Error(error)    => s"Error($error)"
        case Integer(i)      => s"$i"
        case Arr(Some(xs))   => xs.map(_.prettyPrint).mkString("[", ", ", "]")
        case Arr(None)       => "Nil"
      }
    }
  }
}

object CodecInstance extends CodecInstance

trait CodecInstance {
  import RedisMessage.{Status, Error, Integer, Bulk, Arr}

  val eof: ByteVector = "\r\n".bv
  val EOF: Codec[Unit] = constant(eof)

  val longStr: Codec[Long] = {
    def decodeInt(x: String): Attempt[Long] = Attempt.fromTry(Try(x.toLong))
    ascii.exmap[Long](decodeInt, b => Attempt.successful(b.toString))
  }

  val variableLong: Codec[Long]   = VariableSizeDelimited(EOF, longStr, 8L)
  val sizePrefix: Codec[Long]     = variableLong.xmap[Long](_ * 8, _ / 8) // nBytes to nBits
  val variableSizeInt: Codec[Int] = variableLong.xmap[Int](_.toInt, _.toLong)

  val statusCodec: Codec[Status] =
    VariableSizeDelimited(EOF, ascii, 8L).xmap[Status](RedisMessage.status, _.message)

  // hope no one would produce an error with non-ascii value
  val errorCodec: Codec[Error] = VariableSizeDelimited(EOF, ascii, 8L)
    .xmap[Error](Error, _.message)

  val integerCodec: Codec[Integer] = variableLong.xmap[Integer](Integer, _.i)

  val bulkCodec: Codec[Bulk] =
    OptionalVariableSizeCodec[ByteVector](sizePrefix, bytes, EOF)
      .xmap[Bulk](Bulk, _.message)

  val arrCodec: Codec[Arr] = Codec.lazily( // to handle this recursive codec
    OptionalVectorCodec[RedisMessage](variableSizeInt, codec)
      .xmap(Arr, _.message)
  )

  val codec: Codec[RedisMessage] = discriminated[RedisMessage]
    .by(byte)
    .typecase('+', statusCodec)
    .typecase('-', errorCodec)
    .typecase(':', integerCodec)
    .typecase('$', bulkCodec)
    .typecase('*', arrCodec)
}
