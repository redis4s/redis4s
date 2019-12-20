package redis4s

import cats.implicits._
import cats.effect.IO
import redis4s.free.{RedisIO, RedisSession}
import cats.effect.minitest.IOTestSuite

object FreeSuite extends IOTestSuite {
  val session: RedisSession[IO]    = RedisTest.newSession()
  val client: RedisClient[RedisIO] = RedisIO.client

  test("run pipeline") {
    RedisTest.flushAll(session)
    for {
      r <- session.run(client.set("foo", "bar") *> client.get("foo"))
    } yield assertEquals(r, "bar".some)
  }

  test("run transaction") {
    RedisTest.flushAll(session)
    for {
      r <- session.transact(client.set("foo", "bar") *> client.set("foo", "baz") *> client.get("foo"))
    } yield assertEquals(r, "baz".some)
  }

  test("run transaction with watch") {
    RedisTest.flushAll(session)
    val ops  = client.set("foo", "bar") *> client.set("foo", "baz") *> client.get("foo")
    val keys = Vector("foo", "bar", "baz")
    for {
      r <- session.transact(ops, keys)
    } yield assertEquals(r, "baz".some)
  }
}
