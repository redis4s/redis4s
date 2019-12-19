package redis4s

import cats.implicits._

import scala.util.Try
import scala.util.control.NoStackTrace

abstract class RedisError(msg: String, cause: Throwable)           extends RuntimeException(msg, cause)
case class ConnectionClosed()                                      extends RedisError("connection is closed", null)
case class DecodeFailure(msg: String)                              extends RedisError(msg, null)
case class ErrorResponse(error: String)                            extends RedisError(error, null) with NoStackTrace
case class InvalidOperation(detail: String)                        extends RedisError(detail, null)
case class TransactionError(msg: String, cause: Option[Throwable]) extends RedisError(msg, cause.orNull)
case class PipelineError(msg: String)                              extends RedisError(msg, null)
case class ConnectionError(msg: String)                            extends RedisError(msg, null)

object TransactionError {
  def apply(cause: Throwable): TransactionError              = TransactionError("Aborted", cause.some)
  def apply(msg: String): TransactionError                   = TransactionError(msg, none)
  def apply(msg: String, cause: Throwable): TransactionError = TransactionError(msg, cause.some)
}

trait DecodeError extends RedisError with NoStackTrace

object DecodeError {
  def general(msg: String): DecodeError                 = new RedisError(msg, null) with DecodeError
  def exception(msg: String, e: Throwable): DecodeError = new RedisError(msg, e) with DecodeError

  def guard[A](a: => A): Either[DecodeError, A] = Try(a).toEither.leftMap(exception("Error evaluation", _))

  def wrongType(expectedType: String, got: RedisMessage): DecodeError =
    general(s"WrongType: $expectedType expected, got ${got.getClass.getSimpleName}")
}
