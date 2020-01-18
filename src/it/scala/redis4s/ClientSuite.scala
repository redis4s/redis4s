package redis4s

import cats.effect.IO
import cats.effect.testing.minitest.IOTestSuite

trait ClientSuite extends IOTestSuite {
  val redis: RedisClient[IO] = RedisTest.newClient()
  def reset(): Unit          = RedisTest.flushAll(redis)
  reset()
}
