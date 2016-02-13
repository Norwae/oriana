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

  class DBContext extends TestDatabaseContext with DatabaseCommandExecution

  implicit override val timeout = akka.util.Timeout(15.seconds)
  "the database actor (initialization)" should "delay operations until after initialization" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    val executionTime = actor ? { ctx: DatabaseContext =>
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
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
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
    val actorSpec = DatabaseActor.props(new DBContext)
    val observer = system.actorOf(Fuse.props(actorSpec))
    observer ! new FailingInitializer
    observer ! Init

    eventually(Timeout(5.second)) {
      whenReady(observer ? Fuse.ReadStatus) { status =>
        status shouldEqual Blown
      }
    }
  }

  it should "immediately execute operations after init" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    val now = { _: DatabaseContext =>
      Future.successful(System.nanoTime())
    }
    def askForTime(`with`: Any = now) = (actor ? `with`).mapTo[Long]

    val nowFromTx = new DBTransaction[TestDatabaseContext, Long, NoStream, Effect] {
      override def apply(context: TestDatabaseContext) = DBIO.successful(System.nanoTime())
    }

    actor ! Init

    whenReady(askForTime(), Timeout(15.seconds)) { first =>
      whenReady(askForTime()) { second =>
        whenReady(askForTime()) { third =>
          whenReady(askForTime(nowFromTx)) { fourth =>
            (second - first).nanos.toMillis shouldEqual (7L +- 7L)
            (third - second).nanos.toMillis shouldEqual (7L +- 7L)
            (fourth - third).nanos.toMillis shouldEqual (7L +- 7L)
          }
        }
      }
    }
  }

  "the database actors execution mechanism" should "execute operations with a executable context" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init
    val insertResult = actor ? { context: ExecutableDatabaseContext =>

      context.database.run(SingleTestTableAccess.query += (38, "Solidus"))
    }

    whenReady(insertResult.mapTo[Int]) { insertedRows =>
      insertedRows shouldEqual 1
    }
  }

  it should "make transactional attempts a DatabaseTransaction" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init

    val abortiveInsert = actor ? new DBTransaction[TestDatabaseContext, Int, NoStream, Effect.Write] {
      def apply(ctx: TestDatabaseContext) = {
        for {
          _ <- SingleTestTableAccess.query += (192, "Salamander")
          if false
          _ <- SingleTestTableAccess.query += (111, "Sashimi")
        } yield 2
      }
    }

    whenReady(abortiveInsert.failed, Timeout(10.seconds)) { err =>
      val countCreatedRows = actor ? { context: ExecutableDatabaseContext =>
        context.database.run(SingleTestTableAccess.query.filter(row => row.id === 192 || row.id === 111).countDistinct.result)
      }

      whenReady(countCreatedRows) { created =>
        created shouldEqual 0
      }
    }
  }

  it should "commit its transaction on success" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init

    val transactionalInsert = actor ? new DBTransaction[TestDatabaseContext, Int, NoStream, Effect.Write] {
      def apply(ctx: TestDatabaseContext) = {
        for {
          _ <- SingleTestTableAccess.query += (192, "Salamander")
          _ <- SingleTestTableAccess.query += (111, "Sashimi")
        } yield 2
      }
    }

    whenReady(transactionalInsert, Timeout(10.seconds)) { result =>
      result shouldEqual 2
      val countCreatedRows = actor ? { context: ExecutableDatabaseContext =>
        context.database.run(SingleTestTableAccess.query.filter(row => row.id === 192 || row.id === 111).countDistinct.result)
      }

      whenReady(countCreatedRows) { created =>
        created shouldEqual 2
      }
    }
  }

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