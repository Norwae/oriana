package oriana

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import oriana.testdatabase.{DBContext, TestDatabaseContext}
import slick.dbio.DBIOAction

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure

class DatabaseStreamingSpec extends FlatSpec with Matchers with TestActorSystem with ScalaFutures with Eventually {
  implicit val materializer = ActorMaterializer()

  "the source element" should "provide elements to the consumers" in {
    implicit val name = initDatabase()

    val target = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    putInitial(target) {
      val src = executeAsSource { ctx: DBContext =>
        import ctx.api._
        ctx.table.result
      }

      whenReady(src.runWith(Sink.seq): Future[Seq[(Int, String)]]) { it =>
        it should contain theSameElementsInOrderAs target
      }
    }
  }

  "the flow element" should "transform its input, maintaining ordering" in {
    implicit val name = initDatabase()

    val target = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    putInitial(target) {
      val flow: Flow[Int, String, NotUsed] = executeAsFlow { i: Int => { ctx: TestDatabaseContext =>
        import ctx.api._
        (for {
          row <- ctx.table
          if row.id === i
        } yield row.name).result
      }
      }

      val combined = Source(List(1, 2, 3)).via(flow)

      whenReady(combined.runWith(Sink.seq): Future[Seq[String]], Timeout(5.seconds)) { result =>
        result should contain theSameElementsInOrderAs target.map(_._2)
      }
    }
  }

  it should "be able to mutate the stream length" in {
    implicit val name = initDatabase()

    val id1 = UUID.randomUUID().toString
    val id2 = UUID.randomUUID().toString
    val id3 = UUID.randomUUID().toString
    val target = Seq(1 -> id1, 12 -> id2, 36 -> id3)
    putInitial(target) {
      val flow: Flow[Int, String, NotUsed] = executeAsFlow { i: Int => ctx: TestDatabaseContext =>
        import ctx.api._
        (for {
          row <- ctx.table
          if row.id >= i
        } yield row.name).result
      }

      val combined = Source(List(1, 10, 30)).via(flow)

      whenReady(combined.runWith(Sink.seq): Future[Seq[String]], Timeout(5.seconds)) { result =>
        result should contain theSameElementsInOrderAs List(id1, id2, id3, id2, id3, id3)
      }
    }
  }

  "the sink element" should "execute its arguments" in {
    implicit val name = initDatabase()

    val target = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    val source = Source(target)
    val sink = executeAsSink { t: (Int, String) => ctx: TestDatabaseContext =>
      import ctx.api._
      ctx.table += t
    }
    source.to(sink).run()

    eventually(Timeout(10.seconds)) {
      val selected = executeDBOperation { ctx: DBContext =>
        import ctx.api._
        ctx.database.run(ctx.table.result)
      }

      whenReady(selected, Timeout(1.second)) { created =>
        created should contain theSameElementsAs target
      }
    }
  }

  it should "materialize the number of elements executed" in {
    implicit val name = initDatabase()

    val target = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    val source = Source(target)
    val sink = executeAsSink { t: (Int, String) => ctx: TestDatabaseContext =>
      import ctx.api._
      ctx.table += t
    }
    val materialized = source.toMat(sink)(Keep.right).run()

    whenReady(materialized) (_ shouldEqual 3)
  }


  it should "pass along upstream failure to the materialized result" in {
    class UpstreamFailureException extends Exception
    implicit val name = initDatabase()

    val source = Source.failed(new UpstreamFailureException)
    val sink = executeAsSink { t: (Int, String) => ctx: TestDatabaseContext =>
      import ctx.api._
      ctx.table += t
    }
    val materialized = source.toMat(sink)(Keep.right).run()

    eventually(Timeout(10.seconds)) {
      materialized should be('completed)
      val value = materialized.value.get
      value shouldBe a[Failure[_]]
      value.failed.get shouldBe a[UpstreamFailureException]
    }
  }

  it should "support limited parallelism" in {
    implicit val name = initDatabase()

    val target = (0 until 200) map (_ -> UUID.randomUUID().toString)
    val source = Source(target)
    val sink = executeAsSink({ t: (Int, String) => ctx: TestDatabaseContext =>
      import ctx.api._
      ctx.table += t
    }, DBSinkSettings(parallelism = 10))
    val materialized = source.toMat(sink)(Keep.right).run()

    whenReady(materialized) { materialized =>
      materialized shouldEqual 200
      val selected = executeDBOperation { ctx: DBContext =>
        import ctx.api._
        ctx.database.run(ctx.table.result)
      }

      whenReady(selected, Timeout(1.second)) { created =>
        created should contain theSameElementsAs target
      }
    }
  }

  it should "cancel the stream on an error" in {
    val counter = new NextCounter(100)

    implicit val name = initDatabase()

    val source = Source.fromIterator[Int](() => counter.iterator)
    val sink = executeAsSink { t: Int => ctx: TestDatabaseContext =>
      DBIOAction.failed(new NullPointerException)
    }
    val materialized = source.toMat(sink)(Keep.right).run()

    eventually(Timeout(10.seconds)) {
      materialized.value.get shouldBe a [Failure[_]]
      counter.nextCalls shouldEqual 1
    }
  }

  it should "support continuing after errors" in {
    val counter = new NextCounter(100)

    implicit val name = initDatabase()

    val source = Source.fromIterator[Int](() => counter.iterator)
    val sink = executeAsSink({ t: Int => ctx: TestDatabaseContext =>
      DBIOAction.failed(new NullPointerException)
    }, DBSinkSettings(cancelOnError = _ => false))

    val materialized = source.toMat(sink)(Keep.right).run()

    whenReady(materialized, Timeout(100.seconds)){ count =>
      materialized should be ('completed)
      count shouldEqual 0
      counter.nextCalls shouldEqual 100
    }
  }


  def initDatabase() = {
    val database = system.actorOf(DatabaseActor.props(new DBContext))
    database ! NoRetrySchedule
    database ! DatabaseActor.Init
    DatabaseName(database.path)
  }

  def putInitial[T](values: Seq[(Int, String)])(body: => T)(implicit name: DatabaseName) = {
    whenReady(executeDBTransaction { ctx: DBContext =>
      import ctx.api._
      ctx.table ++= values
    }, Timeout(5.seconds))(_ => body)
  }

}
