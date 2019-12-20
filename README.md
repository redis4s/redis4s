# Redis4s

## Example

```scala
import fs2.Stream
import cats.implicits._
import cats.effect._
import redis4s.free.RedisIO
import RedisIO.client

object Example extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val operations: RedisIO[(Option[String], Option[String])] =
      client.set("foo", "bar") *>
        client.set("bar", "baz") *>
        (client.get("bar") product client.get("foo"))

    val f = for {
      redis <- Stream.resource(Redis4s[IO])
      _     <- Stream.eval { redis.run(operations) >>= (a => IO(println(a))) }
    } yield ()

    f.compile.drain.as(ExitCode.Success)
  }
}
```
