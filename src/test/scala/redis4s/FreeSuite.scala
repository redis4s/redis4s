package redis4s

import cats.implicits._
import cats.effect.IO
import redis4s.free.{RedisIO, RedisSession}
import cats.effect.minitest.IOTestSuite

object FreeSuite extends IOTestSuite {
  val session: RedisSession[IO]    = RedisTest.newSession()
  val client: RedisClient[RedisIO] = RedisIO.client
  RedisTest.flushAll(session)

  test("run pipeline") {
    for {
      r <- session.run(client.set("foo", "bar") *> client.get("foo"))
    } yield assertEquals(r, "bar".some)
  }
}
