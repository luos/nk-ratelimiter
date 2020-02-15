package hu.netkatalogus.ratelimit

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

trait AccessStore[T] {
  def accessLogFor(key: T): Seq[LocalDateTime]

  def append(key: T, time: LocalDateTime)

  def cleanup(alreadyExpired: LocalDateTime): Unit

}

class HashMapAccessStore extends AccessStore[String] {
  val accesses = new ConcurrentHashMap[String, Seq[LocalDateTime]]

  override def accessLogFor(key: String): Seq[LocalDateTime] = {
    null
  }

  override def append(key: String, time: LocalDateTime): Unit = {

  }


  def cleanup(alreadyExpired: LocalDateTime): Unit = {
    accesses.forEach((k, v) => {
      val newTimes = v.filter(_.isBefore(alreadyExpired))
      if (newTimes.isEmpty) {
        accesses.remove(k)
      } else {
        accesses.put(k, newTimes)
      }
    })

  }

}

