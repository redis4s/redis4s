package redis4s

import cats.implicits._
import minitest.SimpleTestSuite
import redis4s.CommandCodec.Aux

trait CommandCodecSuite extends SimpleTestSuite {
  import redis4s.ops._
  type Check[A] = (RedisMessage.Arr, Either[RedisError, A])

  def newClient(reply: RedisMessage): RedisClient[Check] = new RedisOps[Check] {
    override def run[R, P](r: R)(implicit codec: Aux[R, P]): Check[P] =
      (RedisMessage.arr(codec.encode(r).toChain.toVector.map(RedisMessage.buf)), codec.decode(reply))
  }

  def given[P](
    name: String
  )(reply: RedisMessage)(cmd: RedisClient[Check] => Check[P])(assertions: Check[P] => Unit): Unit = {
    test(name) {
      assertions {
        cmd(newClient(reply))
      }
    }
  }
}

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
