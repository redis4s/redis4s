package redis4s

sealed trait Order
object Order {
  case object ASC  extends Order
  case object DESC extends Order

  def asc: Order                                          = ASC
  def desc: Order                                         = DESC
  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  implicit val appendable: CommandCodec.Appendable[Order] = CommandCodec.Appendable[String].xmap[Order](_.toString)
}

sealed trait SetModifier
object SetModifier {
  case object NX extends SetModifier
  case object XX extends SetModifier
  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  implicit val appendable: CommandCodec.Appendable[SetModifier] =
    CommandCodec.Appendable[String].xmap[SetModifier](_.toString)
}

sealed trait BitOps
object BitOps {
  case object AND extends BitOps
  case object OR  extends BitOps
  case object XOR extends BitOps
  case object NOT extends BitOps

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  implicit val appendable: CommandCodec.Appendable[BitOps] = CommandCodec.Appendable[String].xmap[BitOps](_.toString)
}
