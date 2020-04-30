package redis4s

import cats.implicits._

import scala.concurrent.duration._

object StringCommandCodecSuite extends CommandCodecSuite {
  import RedisMessage._

  def assertEq(a: RedisMessage, b: RedisMessage): Unit =
    assert(a == b, s"Received ${a.prettyPrint} != expected ${b.prettyPrint}")

  given("empty GET")(`null`)(_.get("foo")) {
    case (req, resp) =>
      assertEq(req, cmd("GET", "foo"))
      assertEquals(resp, none[String].asRight)
  }

  given("GET")(string("bar"))(_.get("baz")) {
    case (req, resp) =>
      assertEq(req, cmd("GET", "baz"))
      assertEquals(resp, "bar".some.asRight)
  }

  given("SET")(status("OK"))(_.set("foo", "bar", 50.seconds, SetModifier.XX.some)) {
    case (req, resp) =>
      assertEq(req, cmd("SET", "foo", "bar", "PX", "50000", "XX"))
      assertEquals(resp, ().asRight)
  }
}
