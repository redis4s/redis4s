package redis4s

import cats.implicits._
import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite

trait ClientSuite extends IOTestSuite {
  val redis: RedisClient[IO] = RedisTest.newTLSClient()
  def reset(): Unit          = RedisTest.flushAll(redis)
  reset()
}

object TLSSuite extends ClientSuite {
  test("get/set on tls") {
    reset()
    for {
      _  <- redis.set("foo", "bar")
      t2 <- redis.get("foo")
    } yield {
      assertEquals(t2, "bar".some)
    }
  }
}
