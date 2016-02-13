package oriana

import java.io.IOException
import java.util.UUID

import akka.actor.{Props, Terminated, Actor, ActorRef}
import akka.pattern.ask
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, ShouldMatchers}
import oriana.DatabaseActor.Init
import oriana.testdatabase.{SingleTestTableAccess, TestDatabaseContext}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class DatabaseActorSpec extends FlatSpec with ShouldMatchers with TestActorSystem with ScalaFutures with Eventually {
  import slick.driver.H2Driver.api._
  import DatabaseActorSpec._
  implicit override val timeout = akka.util.Timeout(15.seconds)
  "the database actor (initialization)" should "delay operations until after initialization" in {
    val actor = system.actorOf(DatabaseActor.props(new TestDatabaseContext))
    val executionTime = actor ? { ctx: TestDatabaseContext =>
      Future.successful(System.nanoTime())
    }

    Thread.sleep(1000)
    val mark = System.nanoTime()
    actor ! DatabaseActor.Init

    whenReady(executionTime.mapTo[Long], Timeout(15.seconds)) { time =>
      time should be > mark
    }
  }

  it should "allow setting another initializer by sending it to the actor" in {
    val token = UUID.randomUUID.toString
    val actor = system.actorOf(DatabaseActor.props(new TestDatabaseContext))
    actor ! new TokenInitializer(token)
    actor ! DatabaseActor.Init

    val getToken = actor ? { ctx: TestDatabaseContext =>
      ctx.database.run(SingleTestTableAccess.query.filter(_.id === 1).map(_.name).result.head)
    }

    whenReady(getToken, Timeout(15.seconds)) { readToken =>
      readToken shouldEqual token
    }
  }
  it should "fail, invoking its supervisor if initialization fails" in {
    val actorSpec = DatabaseActor.props(new TestDatabaseContext)
    val observer = system.actorOf(Fuse.props(actorSpec))
    observer ! new FailingInitializer
    observer ! Init

    eventually(Timeout(5.second)) {
      whenReady(observer ? Fuse.ReadStatus) { status =>
        status shouldEqual Blown
      }
    }

  }

  "the database actors execution mechanism" should "execute operations with a executable context" in pending
  it should "transactionally attempt a DatabaseTransaction" in pending
  it should "commit its transaction on success" in pending

  "the database actors retry handling" should "execute operations once - even if they fail" in pending
  it should "retry transactions according to the default policy" in pending
  it should "allow overriding the retry schedule" in pending
  it should "allow setting up a new default schedule by sending it to the actor" in pending

  "the pimped syntax" should "allow access for operations" in pending
  it should "allow access for transactions" in pending
}


object DatabaseActorSpec {
  class TokenInitializer(token: String) extends DBInitializer {
    override def apply(v1: ExecutableDatabaseContext) = {
      import v1.api._
      val actions = for {
        _ <- SingleTestTableAccess.createDDL
        _ <- SingleTestTableAccess.query += (1, token)
      } yield DatabaseActor.InitComplete
      v1.database.run(actions)
    }
  }

  class FailingInitializer extends DBInitializer {
    override def apply(v1: ExecutableDatabaseContext) = Future.failed(new IOException("Forced failure for initialization"))
  }
}