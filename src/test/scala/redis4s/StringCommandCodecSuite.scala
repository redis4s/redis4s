package redis4s

import cats.implicits._

object StringCommandCodecSuite extends CommandCodecSuite {
  import RedisMessage._

  given("empty GET")(`null`)(_.get("foo")) {
    case (req, resp) =>
      assertEquals(req, cmd("GET", "foo"))
      assertEquals(resp, none[String].asRight)
  }

  given("GET")(string("bar"))(_.get("baz")) {
    case (req, resp) =>
      assertEquals(req, cmd("GET", "baz"))
      assertEquals(resp, "bar".some.asRight)
  }
}
