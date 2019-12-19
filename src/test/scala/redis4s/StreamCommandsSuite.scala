package redis4s

import cats.implicits._

object StreamCommandsSuite extends ClientSuite {
  test("perform xadd/xrange") {
    for {
      _  <- redis.xadd("foo", Map("a" -> "b"))
      xs <- redis.xrange("foo", "-", "+", 10L.some)
    } yield assert(xs.size == 1 && xs.head.body == Map("a" -> "b"))
  }

  test("perform xgroup create") {
    reset()
    for {
      _      <- redis.xgroupCreate("foo", "bar", "0", mkstream = true)
      groups <- redis.xinfoGroups("foo")
      g      = groups.head
    } yield assert(groups.size == 1 && g.pending == 0 && g.name == "bar" && g.consumers == 0)
  }

  test("perform consume/ack/delete ") {
    reset()
    for {
      _            <- redis.xadd("foo", Map("a" -> "b"))
      _            <- redis.xadd("foo", Map("c" -> "d"))
      _            <- redis.xgroupCreate("foo", "grp", "0", mkstream = false)
      streamMsgs   <- redis.xreadGroup("grp", "c1", List("foo" -> ">"), none, none, noack = false)
      stream       <- redis.xinfoStream("foo")
      consumers    <- redis.xinfoConsumers("foo", "grp")
      groupPending <- redis.xpendingGroup("foo", "grp")
      pendings     <- redis.xpending("foo", "grp", "-", "+", 100, none)
      consumer     = consumers.head
      msgs         = streamMsgs.toVector.flatMap(_.messags)
      msgids       = msgs.map(_.id)
      acked        <- redis.xack("foo", "grp", msgids.head, msgids.tail: _*)
      deleted      <- redis.xdel("foo", msgids.head, msgids.tail: _*)
    } yield assert(
      msgs.size == 2 &&
        stream.groups == 1 &&
        stream.length == 2 &&
        consumers.size == 1 &&
        consumer.pending == 2 &&
        acked == 2 &&
        groupPending.total == 2 &&
        pendings.size == 2 &&
        deleted == 2
    )
  }
}
