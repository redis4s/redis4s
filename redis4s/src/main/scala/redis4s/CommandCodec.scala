package redis4s

import shapeless.Lazy
import cats.data.{Chain, NonEmptyChain}
import scodec.bits.ByteVector
import redis4s.internal.ops.stringByteVectorOps
import redis4s.RedisMessageDecoder._

import scala.collection.immutable.Seq

trait CommandCodec[R] {
  type P
  def encode(r: R): NonEmptyChain[ByteVector]
  def decode(r: RedisMessage): Either[RedisError, P]
}

object CommandCodec {
  type Aux[R, P0] = CommandCodec[R] { type P = P0 }

  implicit class ops[R, P](private val codec: Aux[R, P]) extends AnyVal {
    def encodeCommand(r: R): RedisMessage =
      RedisMessage.arr(codec.encode(r).toChain.toVector.map(RedisMessage.buf))
    def decodeResponse(r: RedisMessage): Either[RedisError, P] =
      r.checkError.flatMap(codec.decode)
  }

  def mk[R, P0](enc: R => CommandBuilder)(
    dec: RedisMessage => Either[DecodeError, P0]
  ): CommandCodec.Aux[R, P0] =
    new CommandCodec[R] {
      override type P = P0
      override def encode(r: R): NonEmptyChain[ByteVector]        = enc(r).result
      override def decode(r: RedisMessage): Either[RedisError, P] = dec(r)
    }

  def cmd(a: String, rest: String*): CommandBuilder = CommandBuilder(a.bv, Chain.fromSeq(rest.map(_.bv)))

  case class CommandBuilder(command: ByteVector, args: Chain[ByteVector]) {
    def append[A](a: A)(implicit ap: Lazy[Appendable[A]]): CommandBuilder =
      copy(command, args ++ ap.value.toArgument(a))

    def result: NonEmptyChain[ByteVector] = NonEmptyChain.fromChainPrepend(command, args)
  }

  trait Appendable[A] {
    def toArgument(a: A): Chain[ByteVector]

    def xmap[B](f: B => A): Appendable[B] = Appendable.mk[B](b => toArgument(f(b)))
  }

  object Appendable extends Appendable2 with Appendable1 with Appendable0 {
    implicit val bytes: Appendable[ByteVector] = mk[ByteVector](Chain.one)
    implicit val string: Appendable[String]    = mk[String](s => Chain.one(s.bv))
    implicit val int: Appendable[Int]          = mk[Int](i => Chain.one(i.toString.bv))
    implicit val long: Appendable[Long]        = mk[Long](l => Chain.one(l.toString.bv))
  }

  trait Appendable0 {
    def apply[A: Appendable]: Appendable[A]                           = implicitly[Appendable[A]]
    def mk[A](f: A => Chain[ByteVector]): Appendable[A]               = a => f(a)
    def toArgs[A](a: A)(implicit A: Appendable[A]): Chain[ByteVector] = A.toArgument(a)
  }

  trait Appendable1 { self: Appendable0 =>

    implicit def seq[A: Appendable]: Appendable[Seq[A]] =
      mk[Seq[A]] {
        _.map(toArgs(_))
          .foldLeft(Chain.empty[ByteVector])(_ ++ _)
      }

    implicit def chain[A: Appendable]: Appendable[Chain[A]] =
      mk[Chain[A]] {
        _.toVector
          .map(toArgs(_))
          .foldLeft(Chain.empty[ByteVector])(_ ++ _)
      }

    implicit def nonEmptyChain[A: Appendable]: Appendable[NonEmptyChain[A]] =
      chain[A].xmap[NonEmptyChain[A]](_.toChain)

    implicit def option[A: Appendable]: Appendable[Option[A]] =
      mk[Option[A]] {
        _.map(implicitly[Appendable[A]].toArgument(_))
          .fold(Chain.empty[ByteVector])(identity)
      }
  }

  trait Appendable2 { self: Appendable0 =>
    implicit def optional[A: Appendable]: Appendable[(String, Option[A])] =
      mk[(String, Option[A])] {
        case (name, opt) =>
          opt.fold(Chain.empty[ByteVector])(a => toArgs(a).prepend(name.bv))
      }
    implicit def pair[A: Appendable]: Appendable[(A, A)] =
      mk[(A, A)] {
        case (a, b) => toArgs(a) ++ toArgs(b)
      }
  }
}
