package redis4s.algebra

import cats.data.NonEmptyChain
import cats.implicits._
import redis4s.CommandCodec._
import redis4s.RedisMessageDecoder._

import scala.collection.immutable.Seq

trait KeyCommands[F[_]] {
  // format: OFF
  def del(keys: NonEmptyChain[String]): F[Long]
  def exists(keys: NonEmptyChain[String]): F[Long]
  def expire(key: String, seconds: Long): F[Boolean]
  def expireAt(key: String, at: Long): F[Boolean]
  def keys(pattern: String): F[Seq[String]]
  def move(key: String, db: Int): F[Boolean]
  def persist(key: String): F[Boolean]
  def pttl(key: String): F[Long]
  def randomKey(): F[Option[String]]
  def rename(key: String, newkey: String): F[Unit]
  def renamenx(key: String, newkey: String): F[Boolean]
  def scan(cursor: String, pattern: Option[String], count: Option[Long], `type`: Option[String]): F[KeyCommands.ScanResult]
  def sort(key: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean): F[Seq[String]]
  def sortTo(key: String, dest: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean): F[Long]
  def touch(keys: NonEmptyChain[String]): F[Long]
  def ttl(key: String): F[Long]
  def `type`(key: String): F[String]
  def unlink(keys: NonEmptyChain[String]): F[Long]
  // format: ON
}

object KeyCommands {
  // format: OFF
  case class Del(keys: NonEmptyChain[String])
  case class Exists(keys: NonEmptyChain[String])
  case class Expire(key: String, seconds: Long)
  case class ExpireAt(key: String, at: Long)
  case class Keys(pattern: String)
  case class Move(key: String, db: Int)
  case class Persist(key: String)
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
      mk[Del, Long](d => cmd("DEL").append(d.keys).result)(_.asInteger)
  }

  object Exists {
    implicit val codec: Aux[Exists, Long] =
      mk[Exists, Long](e => cmd("EXISTS").append(e.keys).result)(_.asInteger)
  }

  object Expire {
    implicit val codec: Aux[Expire, Boolean] =
      mk[Expire, Boolean](e => cmd("EXPIRE", e.key, e.seconds.toString).result)(_.asInteger.map(_ == 1L))
  }

  object ExpireAt {
    implicit val codec: Aux[ExpireAt, Boolean] =
      mk[ExpireAt, Boolean](e => cmd("EXPIREAT", e.key, e.at.toString).result)(_.asInteger.map(_ == 1L))
  }

  object Keys {
    implicit val codec: Aux[Keys, Seq[String]] =
      mk[Keys, Seq[String]](k => cmd("KEYS", k.pattern).result)(_.asArray.flatMap(_.traverse(_.asString)))
  }

  object Move {
    implicit val codec: Aux[Move, Boolean] =
      mk[Move, Boolean](m => cmd("MOVE", m.key, m.db.toString).result)(_.asInteger.map(_ == 1L))
  }

  object Persist {
    implicit val codec: Aux[Persist, Boolean] =
      mk[Persist, Boolean](p => cmd("PERSIST", p.key).result)(_.asInteger.map(_ == 1L))
  }

  object Pttl {
    implicit val codec: Aux[Pttl, Long] =
      mk[Pttl, Long](p => cmd("PTTL", p.key).result)(_.asInteger)
  }

  object RandomKey {
    implicit val codec: Aux[RandomKey, Option[String]] =
      mk[RandomKey, Option[String]](_ => cmd("RANDOMKEY").result) { m =>
        m.asString.map(_.some) orElse m.asNil.as(none[String])
      }
  }

  object Rename {
    implicit val codec: Aux[Rename, Unit] =
      mk[Rename, Unit](r => cmd("RENAME", r.key, r.newkey).result)(_.asStatus.void)
  }

  object RenameNx {
    implicit val codec: Aux[RenameNx, Boolean] =
      mk[RenameNx, Boolean](r => cmd("RENAMENX", r.key, r.newkey).result)(_.asInteger.map(_ == 1L))
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
          .result
      } {
        _.asArrayOfSize(2).flatMap {
          case Vector(id, keys) =>
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
          .result
      }(_.asArray.flatMap(_.traverse(_.asString)))
  }

  object SortTo {
    implicit val codec: Aux[SortTo, Long] =
      mk[SortTo, Long] { s =>
        cmd("SORT")
          .append("BY" -> s.by)
          .append(s.limit.map { case (o, c) => Seq(o.toString, c.toString) })
          .append(s.get.flatMap(a => Seq("GET", a)))
          .append(s.order)
          .append(Some("ALPHA").filter(_ => s.alpha))
          .append("TO")
          .append(s.dest)
          .result
      }(_.asInteger)
  }

  //  SORT key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC|DESC] [ALPHA] [STORE destination]
  //  case class Sort(key: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], ordering: Order, alpha: Boolean)
  //  case class SortTo(key: String, dest: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], ordering: Order, alpha: Boolean)

  object Touch {
    implicit val codec: Aux[Touch, Long] =
      mk[Touch, Long](p => cmd("TOUCH").append(p.keys).result)(_.asInteger)
  }

  object Ttl {
    implicit val codec: Aux[Ttl, Long] =
      mk[Ttl, Long](t => cmd("TTL", t.key).result)(_.asInteger)
  }

  object Type {
    implicit val codec: Aux[Type, String] =
      mk[Type, String](t => cmd("TYPE", t.key).result)(_.asStatus)
  }

  object Unlink {
    implicit val codec: Aux[Unlink, Long] =
      mk[Unlink, Long](t => cmd("UNLINK").append(t.keys).result)(_.asInteger)
  }
}
