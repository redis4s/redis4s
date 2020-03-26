package redis4s

import cats.effect.{IO, Timer}
import cats.implicits._
import redis4s.algebra.BitOps

import scala.concurrent.duration._

object StringCommandsSuite extends ClientSuite {
  test("set and get") {
    for {
      _ <- redis.set("foo", "bar")
      r <- redis.get("foo")
    } yield assertEquals(r, "bar".some)
  }

  test("append") {
    for {
      _ <- redis.del("foo", "")
      _ <- redis.append("foo", "bar")
      r <- redis.get("foo")
    } yield assertEquals(r, "bar".some)
  }

  test("bitcount") {
    for {
      _ <- redis.set("foo", "foobar")
      r <- redis.bitcount("foo", none)
      _ = assertEquals(r, 26L)
      r <- redis.bitcount("foo", (0L, 0L).some)
      _ = assertEquals(r, 4L)
    } yield ()
  }

  test("bitop") {
    for {
      _ <- redis.set("foo", "1")
      _ <- redis.set("bar", "0")
      _ <- redis.bitop(BitOps.AND, "baz", "foo", "bar")
      r <- redis.get("baz")
      _ = assertEquals(r, "0".some)
    } yield ()
  }

  test("bitpos") {
    for {
      _ <- redis.set("foo", "k")
      r <- redis.bitpos("foo", 1)
      _ = assertEquals(r, 1L)
    } yield ()
  }

  test("incr decr") {
    for {
      _ <- redis.del("foo")
      _ <- redis.incr("foo")
      r <- redis.get("foo")
      _ = assertEquals(r, "1".some)
      _ <- redis.decr("foo")
      r <- redis.get("foo")
      _ = assertEquals(r, "0".some)
    } yield ()
  }

  test("incrBy decrBy") {
    for {
      _ <- redis.del("foo")
      _ <- redis.incrBy("foo", 50)
      _ <- redis.decrBy("foo", 51)
      r <- redis.get("foo")
      _ = assertEquals(r, "-1".some)
      _ <- redis.incrByFloat("foo", "1e2")
      r <- redis.get("foo")
      _ = assertEquals(r, "99".some)
    } yield ()
  }

  test("getBit setBit") {
    for {
      _ <- redis.setBit("foo", 0L, 1)
      r <- redis.getBit("foo", 0L)
      _ = assertEquals(r, 1L)
    } yield ()
  }

  test("setRange getRange") {
    for {
      _ <- redis.set("foo", "baz")
      _ <- redis.setRange("foo", 0, "bazqux")
      r <- redis.getRange("foo", 1, 3)
      _ = assertEquals(r, "azq")
    } yield ()
  }

  test("getSet") {
    for {
      _ <- redis.set("foo", "baz")
      r <- redis.getSet("foo", "qux")
      _ = assertEquals(r, "baz".some)
      r <- redis.get("foo")
      _ = assertEquals(r, "qux".some)
    } yield ()
  }

  test("mget mset") {
    for {
      _ <- redis.mset("a" -> "1", "b" -> "2", "c" -> "3")
      r <- redis.mget("a", "b", "c")
      _ = assertEquals(r.unite, Vector("1", "2", "3"))
    } yield ()
  }

  test("strlen") {
    for {
      _ <- redis.set("foo", "baz")
      r <- redis.strlen("foo")
      _ = assertEquals(r, 3L)
    } yield ()
  }

  test("setex") {
    for {
      _ <- redis.setex("foo", 1, "bar")
      r <- redis.get("foo")
      _ = assertEquals(r, "bar".some)
      _ <- Timer[IO].sleep(1.second)
      r <- redis.get("foo")
      _ = assertEquals(r, none)
    } yield ()
  }

  test("setnx") {
    for {
      _ <- redis.del("foo")
      _ <- redis.setnx("foo", "bar")
      _ <- redis.setnx("foo", "baz")
      r <- redis.get("foo")
      _ = assertEquals(r, "bar".some)
    } yield ()
  }

  test("msetnx") {
    for {
      _ <- redis.set("foo", "1")
      _ <- redis.del("bar")
      _ <- redis.msetnx("foo" -> "2", "bar" -> "3")
      r <- redis.mget("foo", "bar")
      _ = assertEquals(r, Vector("1".some, none))
    } yield ()
  }

  test("psetex") {
    for {
      _ <- redis.psetex("foo", 100, "1")
      r <- redis.get("foo")
      _ = assertEquals(r, "1".some)
      _ <- Timer[IO].sleep(100.millis)
      r <- redis.get("foo")
      _ = assertEquals(r, none)
    } yield ()
  }
}
