package redis4s

import java.time.Instant

import cats.data.NonEmptyChain
import cats.implicits._
import redis4s.CommandCodec._
import redis4s.RedisMessageDecoder._

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration

trait GenericCommands[F[_]] {
  // format: OFF
  def `type`(key: String): F[String]
  def del(key:String, keys: String*): F[Long]
  def exists(key: String, keys: String*): F[Long]
  def expire(key: String, timeout: FiniteDuration): F[Boolean]
  def expireAt(key: String, timestamp: Instant): F[Boolean]
  def keys(pattern: String): F[Seq[String]]
  def move(key: String, db: Int): F[Boolean]
  def persist(key: String): F[Boolean]
  def pexpire(key: String, timeout: FiniteDuration): F[Boolean]
  def pexpireAt(key: String, timestamp: Instant): F[Boolean]
  def pttl(key: String): F[Option[Long]]
  def randomKey(): F[Option[String]]
  def rename(key: String, newKey: String): F[Unit]
  def renamenx(key: String, newKey: String): F[Boolean]
  def scan(cursor: String, pattern: Option[String], count: Option[Long], `type`: Option[String]): F[GenericCommands.ScanResult]
  def sort(key: String, by: Option[String]=None, limit: Option[(Long, Long)]=None, get: Seq[String]=Nil, order: Option[Order]=None, alpha: Boolean=false): F[Seq[String]]
  def sortTo(key: String, dest: String, by: Option[String]=None, limit: Option[(Long, Long)]=None, get: Seq[String]=Nil, order: Option[Order]=None, alpha: Boolean=false): F[Long]

  def ttl(key: String): F[Option[Long]]
  def unlink(key: String, keys: String*): F[Long]
  // format: ON
}

object GenericCommands {
  // format: OFF
  case class Del(keys: NonEmptyChain[String])
  case class Exists(keys: NonEmptyChain[String])
  case class Expire(key: String, seconds: Long)
  case class ExpireAt(key: String, timestamp: Long)
  case class Keys(pattern: String)
  case class Move(key: String, db: Int)
  case class Persist(key: String)
  case class PExpire(key: String, millisecond: Long)
  case class PExpireAt(key: String, timestampMillis: Long)
  case class Pttl(key: String)
  case class RandomKey()
  case class Rename(key: String, newkey: String)
  case class RenameNx(key: String, newkey: String)
  case class Scan(cursor: String, pattern: Option[String], count: Option[Long], `type`: Option[String])
  case class Sort(key: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean)
  case class SortTo(key: String, dest: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean)
  case class Touch(keys: NonEmptyChain[String])
  case class Ttl(key: String)
  case class Type(key: String)
  case class Unlink(keys: NonEmptyChain[String])
  // format: ON

  case class ScanResult(cursor: String, keys: Seq[String])

  object Del {
    implicit val codec: Aux[Del, Long] =
      mk[Del, Long](d => cmd("DEL").append(d.keys))(_.asInteger)
  }

  object Exists {
    implicit val codec: Aux[Exists, Long] =
      mk[Exists, Long](e => cmd("EXISTS").append(e.keys))(_.asInteger)
  }

  object Expire {
    implicit val codec: Aux[Expire, Boolean] =
      mk[Expire, Boolean](e => cmd("EXPIRE", e.key, e.seconds.toString))(_.asBoolean)
  }

  object ExpireAt {
    implicit val codec: Aux[ExpireAt, Boolean] =
      mk[ExpireAt, Boolean](e => cmd("EXPIREAT", e.key, e.timestamp.toString))(_.asBoolean)
  }

  object Keys {
    implicit val codec: Aux[Keys, Seq[String]] =
      mk[Keys, Seq[String]](k => cmd("KEYS", k.pattern))(_.asArray.flatMap(_.traverse(_.asString)))
  }

  object Move {
    implicit val codec: Aux[Move, Boolean] =
      mk[Move, Boolean](m => cmd("MOVE", m.key, m.db.toString))(_.asBoolean)
  }

  object Persist {
    implicit val codec: Aux[Persist, Boolean] =
      mk[Persist, Boolean](p => cmd("PERSIST", p.key))(_.asBoolean)
  }

  object PExpire {
    implicit val codec: Aux[PExpire, Boolean] =
      mk[PExpire, Boolean](e => cmd("PEXPIRE", e.key, e.millisecond.toString))(_.asBoolean)
  }

  object PExpireAt {
    implicit val codec: Aux[PExpireAt, Boolean] =
      mk[PExpireAt, Boolean](e => cmd("PEXPIREAT", e.key, e.timestampMillis.toString))(_.asBoolean)
  }

  object Pttl {
    implicit val codec: Aux[Pttl, Option[Long]] =
      mk[Pttl, Option[Long]](p => cmd("PTTL", p.key))(_.asInteger.map(x => if (x >= 0) x.some else none))
  }

  object RandomKey {
    implicit val codec: Aux[RandomKey, Option[String]] =
      mk[RandomKey, Option[String]](_ => cmd("RANDOMKEY")) {
        _.asOption.traverse(_.asString)
      }
  }

  object Rename {
    implicit val codec: Aux[Rename, Unit] =
      mk[Rename, Unit](r => cmd("RENAME", r.key, r.newkey))(_.asStatus.void)
  }

  object RenameNx {
    implicit val codec: Aux[RenameNx, Boolean] =
      mk[RenameNx, Boolean](r => cmd("RENAMENX", r.key, r.newkey))(_.asBoolean)
  }

  object Scan {
    implicit val codec: Aux[Scan, ScanResult] =
      mk[Scan, ScanResult] { s =>
        cmd("SCAN", s.cursor)
          .append(s.pattern.as("MATCH"))
          .append(s.pattern)
          .append(s.count.as("COUNT"))
          .append(s.count)
          .append(s.`type`.as("TYPE"))
          .append(s.`type`)
      } {
        _.asArrayOfSize(2).flatMap { case Vector(id, keys) =>
          (id.asString, keys.asArray.flatMap(_.traverse(_.asString))).mapN(ScanResult)
        }
      }
  }

  object Sort {
    implicit val codec: Aux[Sort, Seq[String]] =
      mk[Sort, Seq[String]] { s =>
        cmd("SORT", s.key)
          .append("BY" -> s.by)
          .append(s.limit.map { case (o, c) => Seq(o.toString, c.toString) })
          .append(s.get.flatMap(a => Seq("GET", a)))
          .append(s.order)
          .append(Some("ALPHA").filter(_ => s.alpha))
      }(_.asArray.flatMap(_.traverse(_.asString)))
  }

  object SortTo {
    implicit val codec: Aux[SortTo, Long] =
      mk[SortTo, Long] { s =>
        cmd("SORT")
          .append("BY" -> s.by)
          .append(s.limit.map(x => Seq(x._1.toString, x._2.toString)))
          .append(s.get.flatMap(a => Seq("GET", a)))
          .append(s.order)
          .append(Some("ALPHA").filter(_ => s.alpha))
          .append("TO")
          .append(s.dest)
      }(_.asInteger)
  }

  object Touch {
    implicit val codec: Aux[Touch, Long] =
      mk[Touch, Long](p => cmd("TOUCH").append(p.keys))(_.asInteger)
  }

  object Ttl {
    implicit val codec: Aux[Ttl, Option[Long]] =
      mk[Ttl, Option[Long]](t => cmd("TTL", t.key))(_.asInteger.map(x => if (x >= 0) x.some else none))
  }

  object Type {
    implicit val codec: Aux[Type, String] =
      mk[Type, String](t => cmd("TYPE", t.key))(_.asStatus)
  }

  object Unlink {
    implicit val codec: Aux[Unlink, Long] =
      mk[Unlink, Long](t => cmd("UNLINK").append(t.keys))(_.asInteger)
  }
}
