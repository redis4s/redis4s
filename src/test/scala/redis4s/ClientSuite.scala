package redis4s

import cats.effect.IO
import cats.effect.minitest.IOTestSuite

trait ClientSuite extends IOTestSuite {
  val redis: RedisClient[IO] = RedisTest.yoloClient()
  def reset(): Unit          = RedisTest.flushAll(redis)
  reset()
}
