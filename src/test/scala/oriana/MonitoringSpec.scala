package oriana

import java.io.IOException

import akka.actor.PoisonPill
import akka.util.Timeout
import org.scalatest.{FlatSpec, Matchers}
import oriana.testdatabase.DBContext
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class MonitoringSpec extends FlatSpec with Matchers with TestActorSystem {


  override implicit val timeout: Timeout = Timeout(100.millis)

  class ConfiguredSUT {
    private val childName: String = "db_" + Random.alphanumeric.take(20).mkString
    implicit val actorName = DatabaseName("/user/" + childName)
    val sut = system.actorOf(DatabaseActor.props(new DBContext), childName)
    private val _stats = new CounterStats
    sut ! ExternalInitializer
    sut ! DatabaseActor.Init
    sut ! _stats

    def waitFor(f: Future[Any]) = Await.ready(f, 1.second)

    def evaluateStats() = {
      sut ! PoisonPill
      _stats
    }
  }

  "The ask-timeout counter" should "be incremented by executeDBOperation timeouts" in new ConfiguredSUT {
    waitFor(executeDBOperation { ctx: ExecutableDatabaseContext ⇒
      Future.never
    })

    val stats = evaluateStats()
    stats.timeout shouldEqual 1
    stats.pending shouldEqual 1
    stats.succeeded shouldEqual 0
    stats.failed shouldEqual 0
    stats.retry shouldEqual 0
  }

  it should "be incremented by executeDBTransaction timeouts" in new ConfiguredSUT {
    waitFor(executeDBTransaction { ctx: DatabaseContext ⇒
      DBIOAction.from(Future.never)
    })

    val stats = evaluateStats()
    stats.timeout shouldEqual 1
    stats.pending shouldEqual 1
    stats.succeeded shouldEqual 0
    stats.failed shouldEqual 0
    stats.retry shouldEqual 0
  }

  "Successful operations counter" should "be incremented by successes" in new ConfiguredSUT {
    waitFor(executeDBOperation { ctx: ExecutableDatabaseContext ⇒
      Future.successful(17)
    })

    val stats = evaluateStats()
    stats.timeout shouldEqual 0
    stats.pending shouldEqual 1
    stats.succeeded shouldEqual 1
    stats.failed shouldEqual 0
    stats.retry shouldEqual 0
  }

  "Failed operation counter" should "be incremented by final failures" in new ConfiguredSUT {
    waitFor(executeDBOperation { ctx: ExecutableDatabaseContext ⇒
      Future.failed(new IOException)
    })

    val stats = evaluateStats()
    stats.timeout shouldEqual 0
    stats.pending shouldEqual 1
    stats.succeeded shouldEqual 0
    stats.failed shouldEqual 1
    stats.retry shouldEqual 0
  }

  it should "not be incremented by retries that finally succeeded" in new ConfiguredSUT {
    waitFor(executeDBTransaction(new DBTransaction[DatabaseContext, Int] {
      var count = 0
      override def apply(context: DatabaseContext): DBIOAction[Int, NoStream, Effect.Read with Effect.Write] =
        if (count < 3) {
          count += 1
          DBIOAction.failed(new IOException)
        }
        else DBIOAction.successful(17)

      override def overrideRetrySchedule: Option[RetrySchedule] =
        Some(new FixedRetrySchedule(10.millis, 10.millis, 10.millis))
    }))

    val stats = evaluateStats()
    stats.timeout shouldEqual 0
    stats.pending shouldEqual 1
    stats.succeeded shouldEqual 1
    stats.failed shouldEqual 0
    stats.retry shouldEqual 3
  }

}

class CounterStats extends Monitor {
  var timeout = 0
  var pending = 0
  var succeeded = 0
  var retry = 0
  var failed = 0

  override def executeTimeout(): Unit = timeout += 1
  override def operationPending(): Unit = pending += 1
  override def operationSucceeded(): Unit = succeeded += 1
  override def operationFailed(): Unit = failed += 1
  override def operationRetry(): Unit = retry += 1
}
