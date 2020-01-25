package redis4s

import cats.implicits._
import cats.MonadError
import fs2.Chunk
import fs2.io.tcp.Socket
import scodec.bits.BitVector

import scala.concurrent.duration.FiniteDuration

trait BitVectorSocket[F[_]] {
  def write(bits: BitVector): F[Unit]
  def read(maxBytes: Int): F[BitVector]
}

object BitVectorSocket {
  def wrap[F[_]: MonadError[*[_], Throwable]](socket: Socket[F], socketTimeout: FiniteDuration): BitVectorSocket[F] = {
    new BitVectorSocket[F] {
      override def write(bits: BitVector): F[Unit] = {
        socket.write(Chunk.byteVector(bits.toByteVector), socketTimeout.some)
      }

      override def read(maxBytes: Int): F[BitVector] = {
        socket
          .read(maxBytes, socketTimeout.some)
          .flatMap(_.map(as => BitVector(as.toArray)).liftTo[F](ConnectionClosed()))
      }
    }
  }
}
