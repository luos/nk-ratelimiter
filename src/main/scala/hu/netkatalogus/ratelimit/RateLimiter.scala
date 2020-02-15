package hu.netkatalogus.ratelimit

import java.time.LocalDateTime
import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import hu.netkatalogus.ratelimit.RateLimiter.{Blocked, LimiterConfig, Result, Success}

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

object RateLimiter {

  case class LimiterConfig[T](endpointName: String,
                              limit: Int,
                              duration: FiniteDuration,
                              identity: (T) => String
                             )

  class Result[T]

  case class Success[T](value: T) extends Result[T]

  case class Blocked[T]() extends Result[T]


}

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
  def maybeCleanup = {
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

  def keyCount = accesses.mappingCount()
}
