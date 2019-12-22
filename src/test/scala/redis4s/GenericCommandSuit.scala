package redis4s

import java.time.Instant

import cats.implicits._

import scala.concurrent.duration._

object GenericCommandSuit extends ClientSuite {
  test("type") {
    reset()
    for {
      t1 <- redis.`type`("foo")
      _  <- redis.set("foo", "bar")
      t2 <- redis.`type`("foo")
    } yield {
      assertEquals(t1, "none")
      assertEquals(t2, "string")
    }
  }

  test("del, exists") {
    reset()
    for {
      e1 <- redis.exists("foo")
      a  <- redis.del("foo")
      _  <- redis.set("foo", "a")
      _  <- redis.set("bar", "a")
      _  <- redis.set("baz", "a")
      e2 <- redis.exists("foo", "bar", "baz")
      b  <- redis.del("foo", "bar", "baz")
    } yield {
      assertEquals(a, 0)
      assertEquals(b, 3)
      assertEquals(e1, 0)
      assertEquals(e2, 3)
    }
  }

  test("expire") {
    reset()
    for {
      ttl1 <- redis.ttl("foo")
      _    <- redis.set("foo", "bar")
      ttl2 <- redis.ttl("foo")
      exp  <- redis.expire("foo", 3.seconds)
      pexp <- redis.pexpire("foo", 3.seconds)
      pttl <- redis.pttl("foo")
      ttl  <- redis.ttl("foo")
      bar  <- redis.get("foo")
      _    <- redis.pexpire("foo", 0.second)
      foo  <- redis.get("foo")
    } yield {
      assertEquals(ttl1, none)
      assertEquals(ttl2, none)
      assertEquals(exp, true)
      assertEquals(pexp, true)
      assert(pttl.exists(_ > 0))
      assert(ttl.exists(_ > 0))
      assertEquals(bar, "bar".some)
      assertEquals(foo, none)
    }
  }

  test("expireAt") {
    reset()
    for {
      _                  <- redis.set("foo", "bar")
      currentTimeSeconds <- ioTimer.clock.realTime(SECONDS)
      e1                 <- redis.expireAt("foo", Instant.ofEpochSecond(currentTimeSeconds + 5))
      ttl1               <- redis.ttl("foo")
      e2                 <- redis.pexpireAt("foo", Instant.ofEpochSecond(currentTimeSeconds + 15))
      ttl2               <- redis.pttl("foo")
    } yield {
      assertEquals(e1, true)
      assertEquals(e2, true)
      assert(ttl1.exists(_ > 0))
      assert(ttl2.exists(_ > 0))
    }
  }

  test("persist") {
    reset()
    for {
      _ <- redis.set("foo", "bar")
      a <- redis.persist("foo")
      _ <- redis.expire("foo", 3.seconds)
      b <- redis.persist("foo")
    } yield {
      assertEquals(a, false)
      assertEquals(b, true)
    }
  }

  test("keys") {
    reset()
    for {
      k1 <- redis.randomKey()
      _  <- redis.set("foo", "a")
      _  <- redis.set("bar", "a")
      _  <- redis.set("baz", "a")
      x1 <- redis.keys("b*")
      x2 <- redis.keys("q*")
      k2 <- redis.randomKey()
    } yield {
      assertEquals(x1.toVector.sorted, Vector("bar", "baz").sorted)
      assertEquals(x2.toVector, Vector.empty)
      assertEquals(k1, none)
      assert(k2.exists(List("foo", "bar", "baz").contains))
    }
  }

  test("rename") {
    reset()
    for {
      _    <- redis.set("foo", "a")
      _    <- redis.rename("foo", "bar")
      bar  <- redis.get("bar")
      _    <- redis.set("foo", "b")
      b    <- redis.renamenx("foo", "bar")
      bar0 <- redis.get("bar")
    } yield {
      assertEquals(bar, "a".some)
      assertEquals(bar, bar0)
      assertEquals(b, false)
    }
  }

  test("unlink") {
    reset()
    for {
      _ <- redis.set("foo", "1")
      x <- redis.unlink("foo", "bar")
    } yield {
      assertEquals(x, 1)
    }
  }

  // TODO
  //  scan
  //  sort
  //  sortTo
}
