package redis4s

import cats.implicits._

object StreamCommandsSuite extends ClientSuite {
  test("perform xadd/xrange") {
    for {
      _  <- redis.xadd("foo", Map("a" -> "b"))
      xs <- redis.xrange("foo", "-", "+", 10L.some)
    } yield {
      assertEquals(xs.size, 1)
      assertEquals(xs.head.body, Map("a" -> "b"))
    }
  }

  test("perform xgroup create") {
    reset()
    for {
      _      <- redis.xgroupCreate("foo", "bar", "0", mkstream = true)
      groups <- redis.xinfoGroups("foo")
      g       = groups.head
    } yield {
      assertEquals(groups.size, 1)
      assertEquals(g.pending, 0)
      assertEquals(g.name, "bar")
      assertEquals(g.consumers, 0)
    }
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
      consumer      = consumers.head
      msgs          = streamMsgs.toVector.flatMap(_.messags)
      msgids        = msgs.map(_.id)
      acked        <- redis.xack("foo", "grp", msgids.head, msgids.tail: _*)
      deleted      <- redis.xdel("foo", msgids.head, msgids.tail: _*)
    } yield {
      assertEquals(msgs.size, 2)
      assertEquals(stream.groups, 1)
      assertEquals(stream.length, 2)
      assertEquals(consumers.size, 1)
      assertEquals(consumer.pending, 2)
      assertEquals(acked, 2)
      assertEquals(groupPending.total, 2)
      assertEquals(pendings.size, 2)
      assertEquals(deleted, 2)
    }
  }
}
