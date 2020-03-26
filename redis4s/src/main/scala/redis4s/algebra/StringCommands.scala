package redis4s.algebra

import cats.data.{Chain, NonEmptyChain}
import cats.implicits._
import redis4s.CommandCodec._
import redis4s.RedisMessageDecoder._

import scala.concurrent.duration._

trait StringCommands[F[_]] {
  def set(
    key: String,
    value: String,
    expire: FiniteDuration = 0.seconds,
    setModifier: Option[SetModifier] = None
  ): F[Unit]
  def get(key: String): F[Option[String]]

  def append(key: String, value: String): F[Long]
  def bitcount(key: String, startEnd: Option[(Long, Long)] = None): F[Long]
  def bitop(op: BitOps, destKey: String, srcKey: String, srcKeys: String*): F[Long]
  def bitpos(key: String, bit: Int, start: Option[Long] = None, end: Option[Long] = None): F[Long]
  def decr(key: String): F[Long]
  def incr(key: String): F[Long]
  def decrBy(key: String, decrement: Long): F[Long]
  def incrBy(key: String, increament: Long): F[Long]
  def incrByFloat(key: String, increament: String): F[String]
  def getBit(key: String, offset: Long): F[Long]
  def setBit(key: String, offset: Long, value: Int): F[Long]
  def getRange(key: String, start: Long, end: Long): F[String]
  def setRange(key: String, offset: Long, value: String): F[Long]
  def getSet(key: String, value: String): F[Option[String]]
  def mget(key: String, keys: String*): F[Vector[Option[String]]]
  def mset(pair: (String, String), pairs: (String, String)*): F[Unit]
  def strlen(key: String): F[Long]
  def setex(key: String, seconds: Long, value: String): F[Unit]
  def setnx(key: String, value: String): F[Boolean]
  def msetnx(pair: (String, String), pairs: (String, String)*): F[Boolean]
  def psetex(key: String, millis: Long, value: String): F[Unit]
  def bitfield(key: String, ops: String*): F[Vector[Long]]
}

object StringCommands {
  case class Set(key: String, value: String, expire: FiniteDuration, modifier: Option[SetModifier])
  case class Get(key: String)
  case class Append(key: String, value: String)
  case class BitCount(key: String, startEnd: Option[(Long, Long)])
  case class BitOp(op: BitOps, destKey: String, srcKeys: NonEmptyChain[String])
  case class BitPos(key: String, bit: Int, start: Option[Long], end: Option[Long])
  case class Decr(key: String)
  case class Incr(key: String)
  case class DecrBy(key: String, amount: Long)
  case class IncrBy(key: String, amount: Long)
  case class IncrByFloat(key: String, amount: String)
  case class GetBit(key: String, offset: Long)
  case class SetBit(key: String, offset: Long, value: Int)
  case class GetRange(key: String, start: Long, end: Long)
  case class SetRange(key: String, offset: Long, value: String)
  case class GetSet(key: String, value: String)
  case class MGet(keys: NonEmptyChain[String])
  case class MSet(pairs: NonEmptyChain[(String, String)])
  case class StrLen(key: String)
  case class SetEx(key: String, seconds: Long, value: String)
  case class SetNx(key: String, value: String)
  case class MSetNx(pairs: NonEmptyChain[(String, String)])
  case class PSetEx(key: String, millis: Long, value: String)
  case class BitField(key: String, ops: String*)

  object Set {
    implicit val codec: Aux[Set, Unit] =
      mk[Set, Unit] { s =>
        cmd("SET", s.key, s.value)
          .append("PX" -> s.expire.toMillis.some.filter(_ > 0))
          .append(s.modifier)
      }(_.asStatus.void)
  }

  object Get {
    implicit val codec: Aux[Get, Option[String]] =
      mk[Get, Option[String]](g => cmd("GET", g.key))(
        _.asOption.traverse(_.asString)
      )
  }

  object Append {
    implicit val codec: Aux[Append, Long] = mk[Append, Long](a => cmd("APPEND", a.key, a.value))(_.asInteger)
  }

  object BitCount {
    implicit val codec: Aux[BitCount, Long] =
      mk[BitCount, Long](bc => cmd("BITCOUNT", bc.key).append(bc.startEnd))(_.asInteger)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  object BitOp {
    implicit val codec: Aux[BitOp, Long] =
      mk[BitOp, Long](b => cmd("BITOP").append(b.op).append(b.destKey).append(b.srcKeys))(_.asInteger)
  }

  object BitPos {
    implicit val codec: Aux[BitPos, Long] =
      mk[BitPos, Long](b => cmd("BITPOS", b.key).append(b.bit).append(b.start).append(b.start >> b.end))(_.asInteger)
  }

  object Decr {
    implicit val codec: Aux[Decr, Long] = mk[Decr, Long](d => cmd("DECR", d.key))(_.asInteger)
  }

  object Incr {
    implicit val codec: Aux[Incr, Long] = mk[Incr, Long](d => cmd("INCR", d.key))(_.asInteger)
  }

  object IncrBy {
    implicit val codec: Aux[IncrBy, Long] =
      mk[IncrBy, Long](i => cmd("INCRBY", i.key).append(i.amount))(_.asInteger)
  }

  object DecrBy {
    implicit val codec: Aux[DecrBy, Long] =
      mk[DecrBy, Long](i => cmd("DECRBY", i.key).append(i.amount))(_.asInteger)
  }

  object IncrByFloat {
    implicit val codec: Aux[IncrByFloat, String] =
      mk[IncrByFloat, String](i => cmd("INCRBYFLOAT", i.key).append(i.amount))(_.asString)
  }

  object GetBit {
    implicit val codec: Aux[GetBit, Long] =
      mk[GetBit, Long](a => cmd("GETBIT", a.key).append(a.offset))(_.asInteger)
  }

  object SetBit {
    implicit val codec: Aux[SetBit, Long] =
      mk[SetBit, Long](s => cmd("SETBIT", s.key).append(s.offset).append(s.value))(_.asInteger)
  }

  object GetRange {
    implicit val codec: Aux[GetRange, String] =
      mk[GetRange, String](g => cmd("GETRANGE", g.key).append(g.start).append(g.end))(_.asString)
  }

  object SetRange { //(key: String, offset: Long, value: String)
    implicit val codec: Aux[SetRange, Long] =
      mk[SetRange, Long](s => cmd("SETRANGE", s.key).append(s.offset).append(s.value))(_.asInteger)
  }

  object GetSet {
    implicit val codec: Aux[GetSet, Option[String]] =
      mk[GetSet, Option[String]](s => cmd("GETSET", s.key, s.value))(_.asOption.traverse(_.asString))
  }

  object MGet {
    implicit val codec: Aux[MGet, Vector[Option[String]]] =
      mk[MGet, Vector[Option[String]]](m => cmd("MGET").append(m.keys))(
        _.asArray.flatMap(
          _.traverse(_.asOption.traverse(_.asString))
        )
      )
  }

  object MSet {
    implicit val codec: Aux[MSet, Unit] = mk[MSet, Unit](m => cmd("MSET").append(m.pairs))(
      _.asStatus.void
    )
  }

  object StrLen {
    implicit val codec: Aux[StrLen, Long] = mk[StrLen, Long](s => cmd("STRLEN", s.key))(_.asInteger)
  }

  object SetEx {
    implicit val codec: Aux[SetEx, Unit] =
      mk[SetEx, Unit](s => cmd("SETEX", s.key).append(s.seconds).append(s.value))(_.asStatus.void)
  }

  object SetNx {
    implicit val codec: Aux[SetNx, Boolean] =
      mk[SetNx, Boolean](s => cmd("SETNX", s.key, s.value))(_.asBoolean)
  }

  object MSetNx {
    implicit val codec: Aux[MSetNx, Boolean] =
      mk[MSetNx, Boolean](m => cmd("MSETNX").append(m.pairs))(_.asBoolean)
  }

  object PSetEx {
    implicit val codec: Aux[PSetEx, Unit] =
      mk[PSetEx, Unit](p => cmd("PSETEX", p.key).append(p.millis).append(p.value))(_.asStatus.void)
  }

  object BitField {
    implicit val codec: Aux[BitField, Vector[Long]] =
      mk[BitField, Vector[Long]](p => cmd("BITFIELD", p.key).append(Chain.fromSeq(p.ops)))(
        _.asArray.flatMap(_.traverse(_.asInteger))
      )
  }
}
