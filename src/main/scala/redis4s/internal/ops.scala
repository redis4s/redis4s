package redis4s.internal

import java.nio.charset.StandardCharsets

import cats.implicits._
import cats.data.Chain
import cats.kernel.Monoid
import redis4s.DecodeError
import scodec.{Attempt, Err}
import scodec.bits.{BitVector, ByteVector}

object ops {
  implicit class stringByteVectorOps(private val s: String) extends AnyVal {
    def bv: ByteVector = ByteVector(s.getBytes(StandardCharsets.UTF_8))
  }

  implicit class byteVectorOps(private val bv: ByteVector) extends AnyVal {
    def utf8: Either[DecodeError, String]  = bv.decodeUtf8.leftMap(DecodeError.exception("Unable to decode UTF8", _))
    def ascii: Either[DecodeError, String] = bv.decodeAscii.leftMap(DecodeError.exception("Unable to decode ASCII", _))
  }

  implicit class fromGenericEither(private val x: Attempt.type) extends AnyVal {
    def fromEitherThrow[A](e: Either[Throwable, A]): Attempt[A] = {
      e.fold(ex => Attempt.failure(Err(ex.getMessage)), Attempt.successful)
    }
  }

  implicit class chainOps(private val ch: Chain.type) extends AnyVal {
    def fromOption[A](a: Option[A]): Chain[A] = a.fold(Chain.empty[A])(Chain.one)
  }

  implicit val bitVectorMonoid: Monoid[BitVector] = new Monoid[BitVector] {
    override def empty: BitVector                               = BitVector.empty
    override def combine(x: BitVector, y: BitVector): BitVector = x ++ y
  }
}
