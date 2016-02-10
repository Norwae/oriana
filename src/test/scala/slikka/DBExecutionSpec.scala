package slikka

import java.util.UUID

import akka.actor.{ActorRefFactory, Props}
import akka.pattern.ask
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import org.scalatest.{ShouldMatchers, FlatSpec}
import org.scalatest.concurrent.ScalaFutures

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scala.concurrent.Future

class DBExecutionSpec extends FlatSpec with TestActorSystem with ScalaFutures with ShouldMatchers {
  import DBExecutionSpec._
  "the db execution actor" should "execute its operation when signaled" in {
    val token = UUID.randomUUID()

    val result = performOperationViaExecutionActor() { _ : ExecutableDatabaseContext =>
      Future.successful(token)
    }

    whenReady(result) { sendToken =>
      sendToken shouldEqual token
    }
  }

  it should "retry its operation when prompted" in {
    var attempted = false
    val token = UUID.randomUUID()

    val result = performOperationViaExecutionActor(10.millis){ _ : ExecutableDatabaseContext =>
      if (!attempted) {
        attempted = true
        Future.failed(new NoSuchElementException)
      }
      else Future.successful(token)
    }

    whenReady(result) { sendToken =>
      sendToken shouldEqual token
    }
  }


  it should "delay its retries as defined in the policy" in {
    val delays = mutable.Buffer[Int]()
    val token = UUID.randomUUID()
    val start = System.nanoTime()

    val result = performOperationViaExecutionActor(100.millis, 200.millis, 400.millis) { _ : ExecutableDatabaseContext =>
      if (delays.length < 3) {
        delays += (System.nanoTime() - start).nanos.toMillis.toInt
        Future.failed(new NoSuchElementException)
      }
      else Future.successful(token)
    }

    whenReady(result, Timeout(900.millis)) { sendToken =>
      sendToken shouldEqual token
      delays.size shouldEqual 3
      delays.head shouldEqual (fuzzFactor +- fuzzFactor)
      delays(1) shouldEqual (100 + fuzzFactor +- fuzzFactor)
      delays(2) shouldEqual (300 + fuzzFactor +- fuzzFactor)
    }
  }

  it should "generate a consolidated error when retries are exceeded" in {
    var attempts = 0
    val result = performOperationViaExecutionActor(10.millis, 10.millis, 10.millis) { _ : ExecutableDatabaseContext =>
      attempts += 1
      Future.failed(new NoSuchElementException(attempts.toString))
    }

    whenReady(result.failed) { error =>
      error.getMessage shouldEqual "4"
      error.getSuppressed.map(_.getMessage) should contain theSameElementsInOrderAs List("1", "2", "3")
    }
  }

  it should "fail immediately when no retries are allowed" in {
    val token = UUID.randomUUID()
    val result = performOperationViaExecutionActor(){ _ : ExecutableDatabaseContext =>
      Future.failed(new IllegalArgumentException(token.toString))
    }

    whenReady(result.failed) { error =>
      error.getMessage shouldEqual token.toString
    }
  }

  it should "not attempt further retries after being signalled with a NoRetry exception" in {
    val token = UUID.randomUUID()
    val result = performOperationViaExecutionActor(1.day){ _ : ExecutableDatabaseContext =>
      Future.failed(new IllegalArgumentException(token.toString) with NoRetry)
    }

    whenReady(result.failed) { error =>
      error.getMessage shouldEqual token.toString
    }
  }

  it should "still report suppressed exception on a no-retry failure" in {
    var count = 0
    val token = UUID.randomUUID()
    val result = performOperationViaExecutionActor(10.millis, 1.day){ _ : ExecutableDatabaseContext =>
      count += 1
      Future.failed(if (count == 1) new NoSuchElementException else new IllegalArgumentException(token.toString) with NoRetry)
    }

    whenReady(result.failed) { error =>
      error.getMessage shouldEqual token.toString
      error.getSuppressed.size shouldEqual 1
    }
  }
}

object DBExecutionSpec {
  val fuzzFactor = 50

  def performOperationViaExecutionActor[Result](schedule: FiniteDuration*)(op: DBOperation[ExecutableDatabaseContext, Result])(implicit system: ActorRefFactory, timeout: akka.util.Timeout) = {
    val captor = system.actorOf(Props[SingleMessageCaptor])
    val execution = system.actorOf(DBExecution.props(op, new FixedRetrySchedule(schedule :_*), captor))

    execution ! Start(null)

    captor ? SingleMessageCaptor.Read
  }

}