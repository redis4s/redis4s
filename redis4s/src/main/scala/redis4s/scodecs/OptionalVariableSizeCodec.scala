package redis4s

import cats.syntax.option._
import scodec.{Attempt, Codec, DecodeResult, Decoder, Err, SizeBound}
import scodec.bits.BitVector
import scodec.codecs.{fixedSizeBits, provide}

object OptionalVariableSizeCodec {
  def apply[A](sizeCodec: Codec[Long], valueCodec: Codec[A], eofCodec: Codec[Unit]): OptionalVariableSizeCodec[A] =
    new OptionalVariableSizeCodec[A](sizeCodec, valueCodec, eofCodec)
}

// similar to `VariableSizeCodec`
class OptionalVariableSizeCodec[A](sizeCodec: Codec[Long], valueCodec: Codec[A], eofCodec: Codec[Unit])
    extends Codec[Option[A]] {
  private val decoder: Decoder[Option[A]] = sizeCodec.flatMap { n =>
    if (n < 0) provide[Option[A]](none).asDecoder
    else (fixedSizeBits(n, valueCodec) <~ eofCodec).asDecoder.map(_.some)
  }

  private def fail(a: A, msg: String): Err =
    Err.General(s"failed to encode size of [$a]: $msg", List("size"))

  override def encode(value: Option[A]): Attempt[BitVector] =
    value.fold(sizeCodec.encode(-1L)) { a =>
      for {
        encA    <- valueCodec.encode(a)
        encSize <- sizeCodec.encode(encA.size).mapErr(e => fail(a, e.messageWithContext))
        encEof  <- eofCodec.encode(())
      } yield encSize ++ encA ++ encEof
    }

  override def sizeBound: SizeBound = sizeCodec.sizeBound.atLeast

  override def decode(bits: BitVector): Attempt[DecodeResult[Option[A]]] = decoder.decode(bits)
}
