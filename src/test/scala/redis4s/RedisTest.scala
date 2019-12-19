package redis4s

import cats.implicits._
import cats.effect._
import fs2.io.tcp.SocketGroup
import redis4s.free.{RedisIO, RedisSession}

import scala.concurrent.ExecutionContext

object RedisTest {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val ct: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]     = IO.timer(ec)

  val blocker: Blocker         = Blocker[IO].allocated.unsafeRunSync()._1
  val socketGroup: SocketGroup = SocketGroup[IO](blocker).allocated.unsafeRunSync()._1

  val config: Redis4sConfig[IO] = Redis4sConfig
    .default[IO]
    .copy(socketGroup = Resource.liftF(socketGroup.pure[IO]))

  def allocate[A](resource: Resource[IO, A]): A = resource.allocated.unsafeRunSync()._1

  def yoloClient(): RedisClient[IO] = allocate {
    Redis4s
      .connection(config)
      .map(Redis4s.simple[IO](_))
  }

  def newSession(): RedisSession[IO] = allocate {
    Redis4s[IO](config, Redis4sPoolConfig.default)
  }

  def flushAll(redis: RedisClient[IO]): Unit    = redis.flushAll(false).unsafeRunSync()
  def flushAll(session: RedisSession[IO]): Unit = session.run(RedisIO.client.flushAll(false)).unsafeRunSync()
}
