package redis4s

import minitest.SimpleTestSuite
import redis4s.CommandCodec.Aux

trait CommandCodecSuite extends SimpleTestSuite {
  type Check[A] = (RedisMessage.Arr, Either[RedisError, A])

  def newClient(reply: RedisMessage): RedisClient[Check] =
    new RedisC[Check] {
      override def run[R, P](r: R)(implicit codec: Aux[R, P]): Check[P] =
        (RedisMessage.arr(codec.encode(r).toChain.toVector.map(RedisMessage.buf)), codec.decode(reply))
    }

  def sendRecv[P](
    name: String
  )(reply: RedisMessage)(cmd: RedisClient[Check] => Check[P])(assertions: Check[P] => Unit): Unit = {
    test(name) {
      assertions {
        cmd(newClient(reply))
      }
    }
  }
}
