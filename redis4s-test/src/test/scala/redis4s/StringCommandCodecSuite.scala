package redis4s

import cats.implicits._
import redis4s.algebra.StringCommands.SetModifier

import scala.concurrent.duration._

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

  given("SET")(status("OK"))(_.set("foo", "bar", 50.seconds, SetModifier.XX.some)) {
    case (req, resp) =>
      assertEquals(req, cmd("SET", "foo", "bar", "PX", "50000", "XX"))
      assertEquals(resp, ().asRight)
  }
}
