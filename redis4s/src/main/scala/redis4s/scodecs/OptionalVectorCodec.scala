package redis4s

import cats.syntax.option._
import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound}
import scodec.bits.BitVector
import scodec.codecs.vector

object OptionalVectorCodec {
  def apply[A](countCodec: Codec[Int], valueCodec: Codec[A]): OptionalVectorCodec[A] =
    new OptionalVectorCodec[A](countCodec, valueCodec)
}

// see VectorCodec
class OptionalVectorCodec[A](countCodec: Codec[Int], valueCodec: Codec[A]) extends Codec[Option[Vector[A]]] {
  private val encoder = vector[A](valueCodec).asEncoder

  override def encode(value: Option[Vector[A]]): Attempt[BitVector] = {
    value.fold(countCodec.encode(-1)) { vec =>
      for {
        encSize  <- countCodec.encode(vec.size)
        encValue <- encoder.encode(vec)
      } yield encSize ++ encValue
    }
  }

  override def sizeBound: SizeBound = countCodec.sizeBound.atLeast

  def decodeN(bits: BitVector, n: Int): Attempt[DecodeResult[Vector[A]]] = {
    valueCodec.collect[Vector, A](bits, limit = n.some).flatMap { result =>
      if (result.value.size == n) Attempt.successful(result)
      else
        Attempt.failure(
          Err.insufficientBits(
            needed = bits.size + valueCodec.sizeBound.lowerBound * (n - result.value.size),
            have = bits.size
          )
        )
    }
  }

  override def decode(bits: BitVector): Attempt[DecodeResult[Option[Vector[A]]]] = {
    countCodec.decode(bits).flatMap {
      case DecodeResult(n, bits0) =>
        if (n < 0) Attempt.successful(DecodeResult(none[Vector[A]], bits0))
        else if (n == 0) Attempt.successful(DecodeResult(Vector.empty[A].some, bits0))
        else {
          decodeN(bits0, n).map(_.map(_.some))
        }
    }
  }
}
