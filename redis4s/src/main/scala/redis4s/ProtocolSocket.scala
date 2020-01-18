package redis4s

import cats.data.Chain
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import scodec._
import scodec.bits.BitVector
import redis4s.internal.ops.bitVectorMonoid

trait ProtocolSocket[F[_], A] {
  def read: F[A]
  def readN(n: Int): F[Chain[A]]
  def write(a: A): F[Unit]
  def writeN(as: Chain[A]): F[Unit]
}

object ProtocolSocket {
  private val logger = org.log4s.getLogger
  def pretty(bits: BitVector): String =
    bits.toByteVector.decodeUtf8.fold(_.toString, identity).replaceAll("\r\n", raw"\\r\\n")

  def wrap[F[_]: Sync, A: Codec](
    socket: BitVectorSocket[F],
    readChunkSizeBytes: Int,
    maxResponseSizeBytes: Int
  ): ProtocolSocket[F, A] = {
    new ProtocolSocket[F, A] {
      val bufferRef: Ref[F, BitVector] = Ref.unsafe[F, BitVector](BitVector.empty)
      val codec: Codec[A]              = Codec[A]

      def readChunk: F[BitVector] = socket.read(readChunkSizeBytes)

      override def write(a: A): F[Unit] =
        Codec[A].encode(a).toTry.toEither.liftTo[F] >>= socket.write

      override def writeN(as: Chain[A]): F[Unit] =
        if (as.isEmpty) ().pure[F]
        else as.traverse(Codec[A].encode(_).toTry.toEither).map(_.fold).liftTo[F] >>= socket.write

      def readMany(maxCount: Int, bits: BitVector): F[(Vector[A], BitVector)] = {
        val buffer = Vector.newBuilder[A]

        @scala.annotation.tailrec
        def go(input: BitVector, c: Int): Either[DecodeFailure, (Vector[A], BitVector)] = {
          if (c == 0) (buffer.result() -> input).asRight
          else {
            logger.trace(s"<< ${pretty(input)}")
            codec.decode(input) match {
              case Attempt.Successful(DecodeResult(value, remainder)) =>
                buffer += value
                go(remainder, c - 1)
              case Attempt.Failure(Err.InsufficientBits(_, _, _)) =>
                if (input.sizeLessThan(maxResponseSizeBytes * 8L)) go(input, 0)
                else DecodeFailure(s"Max response size reached (${input.size})").asLeft
              case Attempt.Failure(cause) =>
                DecodeFailure(cause.messageWithContext).asLeft
            }
          }
        }

        go(bits, maxCount).liftTo[F]
      }

      def read0[B](f: BitVector => F[(B, BitVector)]): F[B] =
        for {
          buf            <- bufferRef.getAndSet(BitVector.empty)
          (a, remainder) <- f(buf)
          _              <- bufferRef.set(remainder)
        } yield a

      override def readN(count: Int): F[Chain[A]] = {
        def go(c: Int, bits: BitVector, accu: Chain[A]): F[(Chain[A], BitVector)] = {
          if (c == 0) (accu -> bits).pure[F]
          else
            for {
              bits0                <- readChunk
              (results, remainder) <- readMany(c, bits ++ bits0)
              result               <- go(c - results.size, remainder, accu ++ Chain.fromSeq(results))
            } yield result
        }

        read0(go(count, _, Chain.empty))
          .flatMap { xs =>
            if (xs.size == count) xs.pure[F]
            else ConnectionError(s"Insufficient message read (need ${count} got ${xs.size}").raiseError[F, Chain[A]]
          }
      }

      override def read: F[A] = {
        readN(1).map(_.toList.head)
      }
    }
  }
}
