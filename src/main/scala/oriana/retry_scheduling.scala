package oriana

import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  * Defines a retry schedule for failures of an `DBOperation`.
  */
trait RetrySchedule {
  /**
    * Determines the delay to execute the `retryCount`th retry of an operation
    * @param retryCount retry count - how many times the operation has already been retried
    * @return delay if another attempt should be made, None otherwise
    */
  def retryDelay(retryCount: Int): Option[FiniteDuration]
}

/**
  * Simple schedule that never attempts a retry
  */
object NoRetrySchedule extends RetrySchedule {
  /**
    * Always returns None
    *
    * @param retryCount ignored
    * @return None
    */
  override def retryDelay(retryCount: Int) = None
}

/**
  * Attempts a fixed number of retries, with constant time delays
  * @param retries retry time delays
  */
class FixedRetrySchedule(retries: FiniteDuration*) extends RetrySchedule {
  private val schedule = retries.toArray

  /**
    * Returns the retry delay at position retryCount if present, None otherwise
    * @param retryCount lookup location
    * @return retry delay, if present at that index
    */
  def retryDelay(retryCount: Int) = if (0 <= retryCount && schedule.length > retryCount) Some(schedule(retryCount)) else None
}

/**
  * A configured schedule derives its delays from the config key "retry_db_delay_millis", where it expects to find a
  * number list. The numbers are interpreted as millisecond delays.
  * @param config (sub-)configuration searched for the "retry_db_delay_millis"
  */
class ConfiguredRetrySchedule(config: Config) extends FixedRetrySchedule({
  config.getNumberList("retry_db_delay_millis").map(_.intValue.millis)
} : _*)

/**
  * The default schedule retries five times, with escalating delays. The retries occur at 100, 200, 300, 400 and 500 ms
  * intervals. This schedule is primarily beneficial to testing and demo environments, and should generally be replaced
  * by a more considered schedule in applications.
  */
object DefaultSchedule extends FixedRetrySchedule({
  for (i <- 1 to 5) yield i.millis * 100
}  : _*)