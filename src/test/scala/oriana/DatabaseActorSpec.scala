package oriana

import java.io.IOException
import java.util.UUID

import akka.actor.{Props, Terminated, Actor, ActorRef}
import akka.pattern.ask
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, ShouldMatchers}
import oriana.DBTransaction.FunctionalTransaction
import oriana.DatabaseActor.Init
import oriana.testdatabase.{SingleTestTableAccess, TestDatabaseContext}

import scala.collection.mutable
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
            (second - first).nanos.toMillis shouldEqual (10L +- 10L)
            (third - second).nanos.toMillis shouldEqual (10L +- 10L)
            (fourth - third).nanos.toMillis shouldEqual (10L +- 10L)
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

  "the database actors retry handling" should "execute operations once - even if they fail" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init
    var calls = 0
    val failingOperation = actor ? { ctx: DatabaseContext =>
      calls += 1
      Future.failed(new IOException())
    }

    whenReady(failingOperation.failed, Timeout(5.second)) { _ =>
      calls shouldEqual 1
    }
  }

  it should "retry transactions according to the default policy" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init
    val attemptTimes = mutable.Buffer[Long]()

    val badTx = new DBTransaction[TestDatabaseContext, Long, NoStream, Effect] {
      override def apply(context: TestDatabaseContext) = {
        attemptTimes += System.nanoTime()
        DBIO.failed(new IOException())
      }
    }

    whenReady((actor ? badTx).failed, Timeout(5.second)) { _ =>
      val first = attemptTimes.head
      def delay(i: Int) = (attemptTimes(i) - first).nanos.toMillis.toInt

      attemptTimes.size shouldEqual 6

      delay(1) shouldEqual 100 +- 100
      delay(2) shouldEqual 300 +- 100
      delay(3) shouldEqual 600 +- 100
      delay(4) shouldEqual 1000 +- 100
      delay(5) shouldEqual 1500 +- 100
    }
  }

  it should "allow overriding the retry schedule" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init
    val attemptTimes = mutable.Buffer[Long]()

    val badTx = new DBTransaction[TestDatabaseContext, Long, NoStream, Effect] {
      override def apply(context: TestDatabaseContext) = {
        attemptTimes += System.nanoTime()
        DBIO.failed(new IOException())
      }

      override def overrideRetrySchedule = Some(new FixedRetrySchedule(50.millis, 150.millis, 200.millis))
    }

    whenReady((actor ? badTx).failed, Timeout(5.second)) { _ =>
      val first = attemptTimes.head
      def delay(i: Int) = (attemptTimes(i) - first).nanos.toMillis.toInt

      attemptTimes.size shouldEqual 4

      delay(1) shouldEqual 50 +- 100
      delay(2) shouldEqual 200 +- 100
      delay(3) shouldEqual 400 +- 100
    }
  }

  it should "allow setting up a new default schedule by sending it to the actor" in {
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    actor ! Init
    actor ! new FixedRetrySchedule(175.millis)
    val attemptTimes = mutable.Buffer[Long]()

    val badTx = new DBTransaction[TestDatabaseContext, Long, NoStream, Effect] {
      override def apply(context: TestDatabaseContext) = {
        attemptTimes += System.nanoTime()
        DBIO.failed(new IOException())
      }
    }

    whenReady((actor ? badTx).failed, Timeout(5.second)) { _ =>
      val first = attemptTimes.head
      def delay(i: Int) = (attemptTimes(i) - first).nanos.toMillis.toInt

      attemptTimes.size shouldEqual 2

      delay(1) shouldEqual 175 +- 100
    }
  }

  "the pimped syntax" should "allow access for operations" in {
    val token = UUID.randomUUID.toString
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    implicit val name = DatabaseName(actor.path)
    actor ! Init

    val retrievedToken = executeDBTransaction { _: TestDatabaseContext =>
      DBIO.successful(token)
    }

    whenReady(retrievedToken) { returned =>
      returned shouldEqual token
    }
  }
  it should "allow access for transactions" in {
    val token = UUID.randomUUID.toString
    val actor = system.actorOf(DatabaseActor.props(new DBContext))
    implicit val name = DatabaseName(actor.path)
    actor ! Init

    val retrievedToken = executeDBOperation { _: TestDatabaseContext with DatabaseCommandExecution =>
      Future.successful(token)
    }

    whenReady(retrievedToken) { returned =>
      returned shouldEqual token
    }
  }

  it should "use a default db called 'database' when none is specified" in {
    val token = UUID.randomUUID.toString
    val actor = system.actorOf(DatabaseActor.props(new DBContext), "database")
    actor ! Init

    val retrievedTokenTransactional = executeDBTransaction { ctx: TestDatabaseContext =>
      val action1 = SingleTestTableAccess.query += (1, token)
      val action2 = SingleTestTableAccess.query.filter(_.id === 1).result.head

      action1.flatMap(_ => action2)
    }

    val retrievedToken = executeDBOperation { ctx: TestDatabaseContext with DatabaseCommandExecution =>
      val action1 = SingleTestTableAccess.query += (12, token)
      val action2 = SingleTestTableAccess.query.filter(_.id === 12).result.head

      ctx.database.run(action1.flatMap(_ => action2))
    }

    whenReady(Future.sequence(Seq(retrievedToken, retrievedTokenTransactional))) { result =>
      result should contain theSameElementsAs Set((1, token), (12, token))
    }


  }
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