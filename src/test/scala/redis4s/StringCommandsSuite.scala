package redis4s

import cats.implicits._

object StringCommandsSuite extends ClientSuite {
  test("simple set and get") {
    for {
      _ <- redis.set("foo", "bar")
      r <- redis.get("foo")
    } yield assertEquals(r, "bar".some)
  }
}
