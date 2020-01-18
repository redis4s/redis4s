package redis4s.ops

import java.time.Instant

import cats.data.NonEmptyChain
import redis4s.{CommandCodec, RedisClient}
import redis4s.algebra.{ConnectionCommands, GenericCommands, Order, ServerCommands, StreamCommands, StringCommands}

import scala.collection.immutable.Seq
import scala.concurrent.duration._

trait RedisOps[F[_]]
    extends RedisClient[F]
    with StringOps[F]
    with GenericOps[F]
    with StreamOps[F]
    with ConnectionOps[F]
    with ServerOps[F]
    with RunOps[F]

trait RunOps[F[_]] {
  def run[R, P](r: R)(implicit codec: CommandCodec.Aux[R, P]): F[P]
}

trait GenericOps[F[_]] extends GenericCommands[F] { self: RunOps[F] =>
  import GenericCommands._

  // format: OFF
  override def `type`(key: String): F[String]                                                                                                                      = run(Type(key))
  override def del(key:String, keys: String*): F[Long]                                                                                                             = run(Del(NonEmptyChain(key, keys: _*)))
  override def exists(key: String, keys: String*): F[Long]                                                                                                         = run(Exists(NonEmptyChain(key, keys: _*)))
  override def expire(key: String, timeout: FiniteDuration): F[Boolean]                                                                                            = run(Expire(key, timeout.toSeconds))
  override def expireAt(key: String, timestamp: Instant): F[Boolean]                                                                                               = run(ExpireAt(key, timestamp.getEpochSecond))
  override def keys(pattern: String): F[Seq[String]]                                                                                                               = run(Keys(pattern))
  override def move(key: String, db: Int): F[Boolean]                                                                                                              = run(Move(key, db))
  override def persist(key: String): F[Boolean]                                                                                                                    = run(Persist(key))
  override def pexpire(key: String, timeout: FiniteDuration): F[Boolean]                                                                                           = run(PExpire(key, timeout.toMillis))
  override def pexpireAt(key: String, timestamp: Instant): F[Boolean]                                                                                              = run(PExpireAt(key, timestamp.toEpochMilli))
  override def pttl(key: String): F[Option[Long]]                                                                                                                  = run(Pttl(key))
  override def randomKey(): F[Option[String]]                                                                                                                      = run(RandomKey())
  override def rename(key: String, newkey: String): F[Unit]                                                                                                        = run(Rename(key, newkey))
  override def renamenx(key: String, newkey: String): F[Boolean]                                                                                                   = run(RenameNx(key, newkey))
  override def scan(cursor: String, pattern: Option[String], count: Option[Long], `type`: Option[String]): F[ScanResult]                                           = run(Scan(cursor, pattern, count, `type`))
  override def sort(key: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean): F[Seq[String]]          = run(Sort(key, by, limit, get, order, alpha))
  override def sortTo(key: String, dest: String, by: Option[String], limit: Option[(Long, Long)], get: Seq[String], order: Option[Order], alpha: Boolean): F[Long] = run(SortTo(key, dest, by, limit, get, order, alpha))

  override def ttl(key: String): F[Option[Long]]                                                                                                                           = run(Ttl(key))
  override def unlink(key: String, keys: String*): F[Long]                                                                                                         = run(Unlink(NonEmptyChain(key, keys: _*)))
  // format: ON
}

trait StreamOps[F[_]] extends StreamCommands[F] { self: RunOps[F] =>
  import StreamCommands._

  // format: OFF
  override def xack(key: String, group: String, id: String, ids: String*): F[Long]                                                                                                      = run(XAck(key, group, id, ids.toVector))
  override def xadd(key: String, values: Map[String, String], id: Option[String], maxSizeLimit: Option[Long], maxSizeLimitOption: Option[String]): F[String]                            = run(XAdd(key, id, values, maxSizeLimit, maxSizeLimitOption))
  override def xdel(key: String, id: String, ids: String*): F[Long]                                                                                                                     = run(XDel(key, id, ids.toVector))
  override def xgroupCreate(key: String, group: String, id: String, mkstream: Boolean): F[Unit]                                                                                         = run(XGroupCreate(key, group, id, mkstream))
  override def xgroupDelConsumer(key: String, group: String, consumer: String): F[Long]                                                                                                 = run(XGroupDelConsumer(key, group, consumer))
  override def xgroupDestory(key: String, group: String): F[Long]                                                                                                                       = run(XGroupDestory(key, group))
  override def xgroupSetId(key: String, group: String, id: String): F[Unit]                                                                                                             = run(XGroupSetId(key, group, id))
  override def xinfoStream(key: String): F[XInfoStreamResponse]                                                                                                                         = run(XInfoStream(key))
  override def xinfoGroups(key: String): F[Seq[XInfoGroupResponse]]                                                                                                                     = run(XInfoGroups(key))
  override def xinfoConsumers(key: String, group: String): F[Seq[XInfoConsumerResponse]]                                                                                                = run(XInfoConsumers(key, group))
  override def xlen(key: String): F[Long]                                                                                                                                               = run(XLen(key))
  override def xpending(key: String, group: String, start: String, stop: String, count: Long, consumer: Option[String]): F[Seq[PendingMessage]]                                         = run(XPending(key, group, start, stop, count, consumer))
  override def xpendingGroup(key: String, group: String): F[PendingSummary]                                                                                                             = run(XPendingGroup(key, group))
  override def xrange(key: String, start: String, end: String, count: Option[Long]): F[Seq[StreamMessage]]                                                                              = run(XRange(key, start, end, count))
  override def xread(streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration]): F[Seq[ReadStreamReply]]                                                       = run(XRead(streams, count, block))
  override def xreadGroup(group: String, consumer: String, streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration], noack: Boolean): F[Seq[ReadStreamReply]] = run(XReadGroup(group, consumer, streams, count, block, noack))
  override def xrevRange(key: String, end: String, start: String, count: Option[Long]): F[Seq[StreamMessage]]                                                                           = run(XRevRange(key, end, start, count))
  override def xtrim(key: String, count: Long, option: Option[String]): F[Long]                                                                                                         = run(XTrim(key, count, option))
  // format: ON
}

trait ConnectionOps[F[_]] extends ConnectionCommands[F] { self: RunOps[F] =>
  import ConnectionCommands._

  override def echo(message: String): F[String]          = run(Echo(message))
  override def ping(message: Option[String]): F[Pong]    = run(Ping(message))
  override def swapDb(index1: Int, index2: Int): F[Unit] = run(SwapDb(index1, index2))
}

trait ServerOps[F[_]] extends ServerCommands[F] { self: RunOps[F] =>
  import ServerCommands._

  override def flushAll(async: Boolean): F[Unit] = run(FlushAll(async))
  override def flushDB(async: Boolean): F[Unit]  = run(FlushDB(async))
}

trait StringOps[F[_]] extends StringCommands[F] { self: RunOps[F] =>
  import StringCommands._

  // format: OFF
  override def set(key: String, value: String, expire: FiniteDuration=0.seconds, setModifier: Option[SetModifier]=None): F[Unit] = run(Set(key, value, expire, setModifier))
  override def get(key: String): F[Option[String]]                                                                               = run(Get(key))
  // format: ON
}
