package oriana

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import org.scalatest.{ShouldMatchers, FlatSpec}

import scala.concurrent.duration._
import scala.collection.JavaConversions._

class RetryScheduleSpec extends FlatSpec with ShouldMatchers {
  "The no-retry schedule" should "always return None" in {
    NoRetrySchedule.retryDelay(1) shouldEqual None
  }

  "the default schedule" should "schedule retries at 100, 200, ... 500 ms" in {
    (0 to 5).map(DefaultSchedule.retryDelay) should contain theSameElementsInOrderAs List(Some(100.millis), Some(200.millis), Some(300.millis), Some(400.millis), Some(500.millis), None)
  }

  "the config schedule" should "evaluate 'retry_db_delay_millis' from the config" in {
    val config = ConfigFactory.empty.withValue("retry_db_delay_millis", ConfigValueFactory.fromIterable(Seq(100, 500, 1000).map(Integer.valueOf)))
    val schedule = new ConfiguredRetrySchedule(config)
    (0 to 3).map(schedule.retryDelay) should contain theSameElementsInOrderAs List(Some(100.millis), Some(500.millis), Some(1.second), None)
  }
}
