package hu.netkatalogus.ratelimit

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

import hu.netkatalogus.ratelimit.RateLimiter.{Blocked, LimiterConfig, Result, Success}

import scala.concurrent.duration.FiniteDuration

object RateLimiter {

  /**
   * @param endpointName Name of the endpoint, will be visible in log messages
   * @param limit        Number of requests in the given duration
   * @param duration
   * @param identity     You can provide a function here to derive a rate limiter key from any object,
   *                     For example in Play you have the Request[_] object for any request, you can say that
   *                     this should be the remote IP address (watch out if you use a load balancer, it maybe its IP)
   *                     (r: Request[AnyContent]) => r.remoteAddress
   * @tparam T
   */
  case class LimiterConfig[T](endpointName: String,
                              limit: Int,
                              duration: FiniteDuration,
                              identity: (T) => String
                             )

  sealed class Result[T]

  case class Success[T](value: T) extends Result[T]

  case class Blocked[T]() extends Result[T]

}

/**
 * Limits access to the $endpointName resource, keeps track of the last $limit number of timestamps the resource
 * was accessed.
 * In some small percentage of requests it cleans up the now stale keys.
 *
 * @param limiterConfig
 * @param logger
 * @tparam Id
 */
class RateLimiter[Id](limiterConfig: LimiterConfig[Id], logger: {def warn(s: String): Unit}) {
  private val accesses = new ConcurrentHashMap[String, Seq[LocalDateTime]]

  def apply[T](id: Id, block: () => T): Result[T] = {
    val currentKey = limiterConfig.identity(id)
    if (!check(currentKey)) {
      maybeCleanup
      logger.warn(s"Blocked access for $currentKey to ${limiterConfig.endpointName}")
      Blocked()
    } else {
      maybeCleanup
      Success(block())
    }
  }

  private
  def maybeCleanup(): Unit = {
    if (scala.util.Random.nextDouble() < 0.02) {
      cleanup(allowedTime)
    }
  }

  private
  def check(key: String): Boolean = synchronized {
    val accessTimes: Seq[LocalDateTime] = accesses.get(key)
    if (accessTimes == null) {
      accesses.put(key, Seq(currentTime))
      true
    } else {
      if (accessTimes.length >= limiterConfig.limit) {
        val earliestAllowed = allowedTime
        val validTimes = accessTimes.filter(_.isAfter(earliestAllowed))
        if (validTimes.length < limiterConfig.limit) {
          accesses.put(key, currentTime +: validTimes)
          true
        } else {
          false
        }
      } else {
        accesses.put(key, currentTime +: accessTimes)
        true
      }
    }
  }

  private
  def currentTime: LocalDateTime = LocalDateTime.now()

  private
  def allowedTime = currentTime.minus(limiterConfig.duration.toMillis, ChronoUnit.MILLIS)

  private
  def cleanup(alreadyExpired: LocalDateTime): Unit = {
    accesses.forEach((k, v) => {
      val newTimes = v.filter(_.isAfter(alreadyExpired))
      if (newTimes.isEmpty) {
        accesses.remove(k)
      } else {
        accesses.put(k, newTimes)
      }
    })
  }

  def cleanup(): Unit = cleanup(allowedTime)

  def keyCount: Long = accesses.mappingCount()
}
