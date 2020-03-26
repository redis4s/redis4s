package redis4s.algebra

import cats.data.Chain
import cats.implicits._
import redis4s.CommandCodec._
import redis4s.{DecodeError, RedisMessage}
import redis4s.RedisMessageDecoder._
import redis4s.algebra.StreamCommands.{XInfoConsumerResponse, XInfoGroupResponse, XInfoStreamResponse}
import redis4s.internal.ops.chainOps

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration

// format: OFF
trait StreamCommands[F[_]] {
  def xack(key: String, group: String, id: String, ids: String*): F[Long]
  def xadd(key: String, values: Map[String, String], id: Option[String]=none, maxSizeLimit: Option[Long]=none, maxSizeLimitOption: Option[String]=none): F[String]
  // def xclaim()
  def xdel(key: String, id: String, ids: String*): F[Long]
  def xgroupCreate(key: String, group: String, id: String, mkstream: Boolean): F[Unit]
  def xgroupDelConsumer(key: String, group: String, consumer: String): F[Long]
  def xgroupDestory(key: String, group: String): F[Long]
  def xgroupSetId(key: String, group: String, id: String): F[Unit]
  def xinfoStream(key: String): F[XInfoStreamResponse]
  def xinfoGroups(key: String): F[Seq[XInfoGroupResponse]]
  def xinfoConsumers(key: String, group: String): F[Seq[XInfoConsumerResponse]]
  def xlen(key: String): F[Long]
  def xpending(key: String, group: String, start: String, stop: String, count: Long, consumer: Option[String]): F[Seq[StreamCommands.PendingMessage]]
  def xpendingGroup(key: String, group: String): F[StreamCommands.PendingSummary]
  def xrange(key: String, start: String, end: String, count: Option[Long]): F[Seq[StreamCommands.StreamMessage]]
  def xread(streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration]): F[Seq[StreamCommands.ReadStreamReply]]
  def xreadGroup(group: String, consumer: String, streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration], noack: Boolean): F[Seq[StreamCommands.ReadStreamReply]]
  def xrevRange(key: String, end: String, start: String, count: Option[Long]): F[Seq[StreamCommands.StreamMessage]]
  def xtrim(key: String, count: Long, option: Option[String]): F[Long]
}
// format: ON

// format: OFF
object StreamCommands {
  case class XAck(key: String, group: String, id: String, ids: Seq[String])
  case class XAdd(key: String, id: Option[String], values: Map[String, String], maxSizeLimit: Option[Long], maxSizeLimitOption: Option[String])
  case class XTrim(key: String, count: Long, option: Option[String])
  case class XDel(key: String, id: String, ids: Seq[String])
  case class XLen(key: String)
  case class XRange(key: String, start: String, end: String, count: Option[Long])
  case class XRevRange(key: String, end: String, start: String, count: Option[Long])
  case class XRead(streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration])
  case class XPending(key: String, group: String, start: String, end: String, count: Long, consumer: Option[String])
  case class XPendingGroup(key: String, group: String)
  case class XReadGroup(group: String, consumer: String, streams: Seq[(String, String)], count: Option[Long], block: Option[FiniteDuration], noack: Boolean)

  case class XGroupCreate(key: String, group: String, id: String, mkstream: Boolean)
  case class XGroupSetId(key: String, group: String, id: String)
  case class XGroupDestory(key: String, group: String)
  case class XGroupDelConsumer(key: String, group: String, consumer: String)

  case class XInfoStream(key: String)
  case class XInfoGroups(key: String)
  case class XInfoConsumers(key: String, group: String)

  case class XInfoStreamResponse(length: Long, radixTreeKeys: Long, radixTreeNodes: Long, groups: Long, lastGeneratedId: String)
  case class XInfoGroupResponse(name: String, consumers: Long, pending: Long, lastDeliveredId: String)
  case class XInfoConsumerResponse(name: String, pending: Long, idle: Long)

  case class StreamMessage(id: String, body: Map[String, String])
  case class PendingMessage(id: String, consumer: String, elapsedMillis: Long, numDelivers: Long)
  case class PendingSummary(total: Long, start: String, end: String, consumers: Map[String, Long])
  case class ReadStreamReply(stream: String, messags: Seq[StreamMessage])
  // format: ON

  object PendingSummary {
    def decode(m: RedisMessage): Either[DecodeError, PendingSummary] = {
      m.asArrayOfSize(4).flatMap {
        case Vector(total, start, end, details) =>
          val parsedDetails = details.asArray
            .flatMap {
              _.traverse {
                _.asArrayOfSize(2).flatMap {
                  case Vector(k, v) =>
                    (k.asString, v.asString.flatMap(n => DecodeError.guard(n.toLong))).tupled
                }
              }
            }
            .map(_.toMap)
          (total.asInteger, start.asString, end.asString, parsedDetails).mapN(PendingSummary.apply)
      }
    }
  }

  object StreamMessage {
    def decode(m: RedisMessage): Either[DecodeError, StreamMessage] = m.asArrayOfSize(2).flatMap {
      case Vector(id, arr) =>
        (id.asString, arr.asStringMap).mapN(StreamMessage.apply)
    }

    def decodeSeq(m: RedisMessage): Either[DecodeError, Seq[StreamMessage]] =
      m.asArray.flatMap {
        _.traverse(decode)
      }
  }

  object PendingMessage {
    def decode(m: RedisMessage): Either[DecodeError, PendingMessage] = {
      m.asArrayOfSize(4).flatMap {
        case Vector(id, consumer, elapsedMillis, numDelivers) =>
          (id.asString, consumer.asString, elapsedMillis.asInteger, numDelivers.asInteger).mapN(PendingMessage.apply)
      }
    }
  }

  object ReadStreamReply {
    def decode(m: RedisMessage): Either[DecodeError, ReadStreamReply] =
      m.asArrayOfSize(2).flatMap {
        case Vector(stream, messages) =>
          val messages0 = messages.asArray.flatMap(_.traverse(StreamMessage.decode))
          (stream.asString, messages0).mapN(ReadStreamReply.apply)
      }

    def decodeSeq(m: RedisMessage): Either[DecodeError, Seq[ReadStreamReply]] =
      m.nilAsEmpty.asArray
        .flatMap(_.traverse(decode))
  }

  object XAck {
    implicit val codec: Aux[XAck, Long] = mk[XAck, Long] {
      case XAck(key, group, id, ids) =>
        cmd("XACK", key, group, id).append(ids)
    }(_.asInteger)
  }

  object XAdd {
    implicit val codec: Aux[XAdd, String] = mk[XAdd, String] {
      case XAdd(key, id, values, maxSizeLimit, maxSizeLimitOption) =>
        val sizeLimit = for {
          limit  <- maxSizeLimit
          option = Chain.fromOption(maxSizeLimitOption)
        } yield Chain.one("MAXLEN") ++ option ++ Chain.one(limit.toString)

        val pairs = values.toVector.flatMap { case (a, b) => Vector(a, b) }

        cmd("XADD", key, id.getOrElse("*"))
          .append(sizeLimit.getOrElse(Chain.empty))
          .append(Chain.fromSeq(pairs))
    } { _.asString }
  }

  object XTrim {
    implicit val codec: Aux[XTrim, Long] =
      mk[XTrim, Long] {
        case XTrim(key, count, option) =>
          cmd("XTRIM", key, "MAXLEN").append(option).append(count.toString)
      } {
        _.asInteger
      }
  }

  object XDel {
    implicit val codec: Aux[XDel, Long] =
      mk[XDel, Long] {
        case XDel(key, id, ids) =>
          cmd("XDEL", key, id).append(ids)
      }(_.asInteger)
  }

  object XLen {
    implicit val codec: Aux[XLen, Long] =
      mk[XLen, Long](l => cmd("XLEN", l.key))(_.asInteger)
  }

  object XRange {
    implicit val codec: Aux[XRange, Seq[StreamMessage]] =
      mk[XRange, Seq[StreamMessage]] { r =>
        val count = r.count.fold(Chain.empty[String])(x => Chain("COUNT", x.toString))
        cmd("XRANGE", r.key, r.start, r.end)
          .append(count)
      }(StreamMessage.decodeSeq)
  }

  object XRevRange {
    implicit val codec: Aux[XRevRange, Seq[StreamMessage]] =
      mk[XRevRange, Seq[StreamMessage]] {
        case XRevRange(key, end, start, count) =>
          cmd("XREVRANGE", key, start, end)
            .append("COUNT" -> count)
      }(StreamMessage.decodeSeq)
  }

  object XGroupCreate {
    implicit val codec: Aux[XGroupCreate, Unit] =
      mk[XGroupCreate, Unit] { x =>
        val mkstream = if (x.mkstream) Chain.one("MKSTREAM") else Chain.empty
        cmd("XGROUP", "CREATE", x.key, x.group, x.id).append(mkstream)
      } { _.asStatus.void }
  }

  object XGroupSetId {
    implicit val codec: Aux[XGroupSetId, Unit] =
      mk[XGroupSetId, Unit] { x => cmd("XGROUP", "SETID", x.key, x.group, x.id) } { _.asStatus.void }
  }

  object XGroupDestory {
    implicit val codec: Aux[XGroupDestory, Long] =
      mk[XGroupDestory, Long] { x => cmd("XGROUP", "DESTROY", x.key, x.group) } { _.asInteger }
  }

  object XGroupDelConsumer {
    implicit val codec: Aux[XGroupDelConsumer, Long] =
      mk[XGroupDelConsumer, Long] { x => cmd("XGROUP", "DELCONSUMER", x.key, x.group, x.consumer) } {
        _.asInteger
      }
  }

  object XRead {
    implicit val codec: Aux[XRead, Seq[ReadStreamReply]] = mk[XRead, Seq[ReadStreamReply]] {
      case XRead(streams, count, block) =>
        cmd("XREAD")
          .append("COUNT" -> count)
          .append("BLOCK" -> block.map(_.toMillis).filter(_ > 0))
          .append("STREAMS")
          .append(streams.map(_._1))
          .append(streams.map(_._2))
    }(ReadStreamReply.decodeSeq)
  }

  object XPending {
    implicit val codec: Aux[XPending, Seq[PendingMessage]] =
      mk[XPending, Seq[PendingMessage]] { x =>
        cmd("XPENDING", x.key, x.group, x.start, x.end, x.count.toString)
          .append(x.consumer)
      }(m => m.asArray.flatMap(_.traverse(PendingMessage.decode)))
  }

  object XPendingGroup {
    implicit val codec: Aux[XPendingGroup, PendingSummary] =
      mk[XPendingGroup, PendingSummary](x => cmd("XPENDING", x.key, x.group))(PendingSummary.decode)
  }

  object XReadGroup {
    implicit val codec: Aux[XReadGroup, Seq[ReadStreamReply]] = mk[XReadGroup, Seq[ReadStreamReply]] {
      case XReadGroup(group, consumer, streams, count, block, noack) =>
        cmd("XREADGROUP", "GROUP", group, consumer)
          .append("COUNT" -> count)
          .append("BLOCK" -> block.map(_.toMillis).filter(_ > 0))
          .append("NOACK".some.filter(_ => noack))
          .append("STREAMS")
          .append(streams.map(_._1))
          .append(streams.map(_._2))
    }(ReadStreamReply.decodeSeq)
  }

  object XInfoStream {
    implicit val codec: Aux[XInfoStream, XInfoStreamResponse] =
      mk[XInfoStream, XInfoStreamResponse](x => cmd("XINFO", "STREAM", x.key)) {
        _.asMap.flatMap { m =>
          (
            m.access("length") >>= (_.asInteger),
            m.access("radix-tree-keys") >>= (_.asInteger),
            m.access("radix-tree-nodes") >>= (_.asInteger),
            m.access("groups") >>= (_.asInteger),
            m.access("last-generated-id") >>= (_.asString)
          ).mapN(XInfoStreamResponse)
        }
      }
  }

  object XInfoGroups {
    implicit val codec: Aux[XInfoGroups, Seq[XInfoGroupResponse]] =
      mk[XInfoGroups, Seq[XInfoGroupResponse]](x => cmd("XINFO", "GROUPS", x.key)) {
        _.asArray.flatMap {
          _.traverse {
            _.asMap.flatMap { m =>
              (
                m.access("name") >>= (_.asString),
                m.access("consumers") >>= (_.asInteger),
                m.access("pending") >>= (_.asInteger),
                m.access("last-delivered-id") >>= (_.asString)
              ).mapN(XInfoGroupResponse)
            }
          }
        }
      }
  }

  object XInfoConsumers {
    implicit val codec: Aux[XInfoConsumers, Seq[XInfoConsumerResponse]] =
      mk[XInfoConsumers, Seq[XInfoConsumerResponse]](x => cmd("XINFO", "CONSUMERS", x.key, x.group)) {
        _.asArray.flatMap {
          _.traverse {
            _.asMap.flatMap { m =>
              (
                m.access("name") >>= (_.asString),
                m.access("pending") >>= (_.asInteger),
                m.access("idle") >>= (_.asInteger)
              ).mapN(XInfoConsumerResponse)
            }
          }
        }
      }
  }
}
