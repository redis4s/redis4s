package redis4s.free

import redis4s.CommandCodec

sealed trait RequestOp[A]
object RequestOp {
  final case class Req[R, A](req: R, codec: CommandCodec.Aux[R, A]) extends RequestOp[A]
}
