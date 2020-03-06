# Redis4s

## Example

```scala
import cats.Show
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

    Redis4s[IO]
      .use { session =>
        session.run(operations) >>= putStrLn
      }
      .as(ExitCode.Success)
  }

  def putStrLn[A: Show](a: A): IO[Unit] = IO(println(a.show))
}
```
