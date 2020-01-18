package redis4s

import java.nio.charset.StandardCharsets

import minitest._
import redis4s.{RedisMessage => RM}
import scodec.Err
import scodec.bits.ByteVector

object MessageCodecSuite extends SimpleTestSuite {
  def p(s: String): Either[Err, RedisMessage] = {
    val value = ByteVector(s.getBytes(StandardCharsets.UTF_8)).bits
    RM.codec.decodeValue(value).toEither
  }

  test("should parse simple messages") {
    assertEquals(p("+OK\r\n"), Right(RM.Status("OK")))
    assertEquals(p("-Some error\r\n"), Right(RM.Error("Some error")))
    assertEquals(p("$6\r\nfoobar\r\n"), Right(RM.string("foobar")))
    assertEquals(p("$0\r\n\r\n"), Right(RM.string("")))
    assertEquals(p("*0\r\n"), Right(RM.arr(Vector.empty)))
    assertEquals(p(":-9998\r\n"), Right(RM.Integer(-9998L)))
  }

  test("should fail on incomplete message") {
    assert(p("*1\r\n*2\r\n$4\r\ntest\r\n").isLeft)
  }

  test("should parse array and deal with corner cases") {
    assertEquals(
      p("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"),
      Right(
        RM.arr(Vector(RM.string("foo"), RM.string("bar")))
      )
    )
    assertEquals(p("$-1\r\n"), Right(RM.`null`))
    assertEquals(p("*-1\r\n"), Right(RM.nil))
  }
}
