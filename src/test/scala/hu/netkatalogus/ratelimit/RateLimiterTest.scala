package hu.netkatalogus.ratelimit

import java.util.concurrent.TimeUnit

import hu.netkatalogus.ratelimit.RateLimiter.{Blocked, LimiterConfig, Success}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration

class RateLimiterTest extends AnyFlatSpec with Matchers {
  val logMessages = new ListBuffer[String]()

  def warn(log: String): Unit = {
    logMessages.append(log)
  }

  val timeInterval = new FiniteDuration(500, TimeUnit.MILLISECONDS)
  val config = LimiterConfig("test-endpoint", 3, timeInterval, (s: String) => s)

  "The Rate Limiter" should "perform requests if there was no previous request" in {
    val limiter: RateLimiter[String] = new RateLimiter[String](config, this)
    assert(limiter.apply("user-id", () => 1) == Success(1))
    assert(limiter.apply("user-id", () => 2) == Success(2))
    assert(limiter.apply("user-id", () => 3) == Success(3))
  }

  "The Rate Limiter" should "should block  request if more requests were made in the allowed time" in {
    val limiter = new RateLimiter[String](config, this)
    assert(limiter.apply("user-id", () => 1) == Success(1))
    assert(limiter.apply("user-id", () => 2) == Success(2))
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 4) == Blocked())
  }

  "The Rate Limiter" should "should unblock if the time elapsed" in {
    val limiter = new RateLimiter[String](config, this)
    (1 to 3).foreach(_ => {
      limiter.apply("user-id", () => 1)
    })
    assert(limiter.apply("user-id", () => 4) == Blocked())
    Thread.sleep(501)
    assert(limiter.apply("user-id", () => 3) == Success(3))
  }

  "The Rate Limiter" should "should block and unblock multiple times" in {
    val limiter = new RateLimiter[String](config, this)
    (1 to 3).foreach(_ => {
      limiter.apply("user-id", () => 1)
      Thread.sleep(100)
    })
    assert(limiter.apply("user-id", () => 4) == Blocked())
    Thread.sleep(201)
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Blocked())
    Thread.sleep(501)
    assert(limiter.apply("user-id", () => 3) == Success(3))
  }

  "The Rate Limit" should "clean up expired content" in {
    val limiter = new RateLimiter[String](config, this)
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Success(3))
    Thread.sleep(501)
    limiter.cleanup()
    assert(limiter.keyCount == 0)
  }

  "The Rate Limit" should "should support multiple keys" in {
    val limiter = new RateLimiter[String](config, this)
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Success(3))
    assert(limiter.apply("user-id", () => 3) == Blocked())
    assert(limiter.apply("user-id-2", () => 1) == Success(1))
    assert(limiter.keyCount == 2)
  }
}
