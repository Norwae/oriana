package oriana

import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import java.util.UUID

import akka.NotUsed
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import oriana.testdatabase.{DBContext, SingleTestTableAccess, TestDatabaseContext}
import slick.backend.DatabasePublisher

import scala.concurrent.duration._
import slick.dbio.DBIOAction

import scala.collection.immutable.Seq
import scala.concurrent.Future


class DatabaseStreamingSpec extends FlatSpec with Matchers with TestActorSystem with ScalaFutures {
  implicit val materializer = ActorMaterializer()

  "the source element" should "provide elements to the consumers" in {
    implicit val name = initDatabase()

    val target  = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    putInitial(target){
      val src = executeAsSource { ctx: DBContext =>
        import ctx.api._
        SingleTestTableAccess.query.result
      }

      whenReady(src.runWith(Sink.seq): Future[Seq[(Int, String)]]) { it =>
        it should contain theSameElementsInOrderAs target
      }
    }
  }

  it should "be throttled by later stages" in {
    implicit val name = initDatabase()

    val target  = Seq(1 -> UUID.randomUUID().toString, 2 -> UUID.randomUUID().toString, 3 -> UUID.randomUUID().toString)
    putInitial(target){
      val src = executeAsSource { ctx: DBContext =>
        import ctx.api._
        SingleTestTableAccess.query.result
      }

      val throttled: Source[LocalDateTime, NotUsed] = src.map(_ => LocalDateTime.now).throttle(1, 1.second, 1, ThrottleMode.Shaping)
      whenReady(throttled.runWith(Sink.seq): Future[Seq[LocalDateTime]], Timeout(5.seconds)) { it =>
        it.size shouldEqual 3
        val reference = it.head.toEpochSecond(ZoneOffset.UTC)
        it(1).toEpochSecond(ZoneOffset.UTC) shouldEqual reference + 1 +- 1
        it(1).toEpochSecond(ZoneOffset.UTC) shouldEqual reference + 2 +- 1
      }
    }
  }

  def initDatabase() = {
    val database = system.actorOf(DatabaseActor.props(new DBContext))
    database ! DatabaseActor.Init
    DatabaseName(database.path)
  }

  "the flow element" should "transform its input, maintaining ordering" in {

  }

  it should "be able to mutate the stream length" in pending


  def putInitial[T](values: Seq[(Int, String)])(body: =>T)(implicit name: DatabaseName) = {
    whenReady(executeDBTransaction { ctx: DBContext =>
      import ctx.api._
      SingleTestTableAccess.query ++= values
    }, Timeout(5.seconds)) (_ => body)
  }

}
