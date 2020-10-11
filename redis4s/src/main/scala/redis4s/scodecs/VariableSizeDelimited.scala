package redis4s.scodecs

import scodec._
import scodec.bits.BitVector

// `VariableSizeDelimitedCodec` will fail if delimiter is not found
// this replaces the hard failure with `InsufficientBits`
object VariableSizeDelimited {
  def apply[A](delimiterCodec: Codec[Unit], valueCodec: Codec[A], multipleValueSize: Long = 0L): Codec[A] = {
    val codec = codecs.variableSizeDelimited(delimiterCodec, valueCodec, multipleValueSize)
    new VariableSizeDelimited[A](codec)
  }
}

class VariableSizeDelimited[A](wrapped: Codec[A]) extends Codec[A] {
  override def encode(value: A): Attempt[BitVector] = wrapped.encode(value)

  override def sizeBound: SizeBound = wrapped.sizeBound

  override def decode(bits: BitVector): Attempt[DecodeResult[A]] = {
    wrapped.decode(bits).recoverWith { case _ =>
      Attempt.Failure(Err.insufficientBits(bits.size + 1, bits.size))
    }
  }
}
