package redis4s.algebra

import redis4s.CommandCodec

sealed trait Order
object Order {
  case object ASC  extends Order
  case object DESC extends Order

  def asc: Order  = ASC
  def desc: Order = DESC

  implicit val appendable: CommandCodec.Appendable[Order] = CommandCodec.Appendable[String].xmap[Order](_.toString)
}
