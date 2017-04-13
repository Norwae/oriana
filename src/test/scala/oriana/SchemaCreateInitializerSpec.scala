package oriana



import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import slick.dbio.Effect.{Read, Write}
import slick.dbio.{DBIOAction, NoStream}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SchemaCreateInitializerSpec extends FlatSpec with Matchers with ScalaFutures {
  import testdatabase._

  def insertAndQueryExampleDataFromTable(ctx: TestDatabaseContext with DatabaseCommandExecution): DBIOAction[Seq[Int], NoStream, Write with Read] = {
    import ctx.api._
    for {
      _ <- ctx.table +=(1, "foo")
      id <- ctx.table.filter(_.name === "foo").map(_.id).result
    } yield id
  }
  "the schema creating initializer" should "create a  usable schema" in {
    val context = new TestDatabaseContext with DatabaseCommandExecution

    whenReady(SchemaCreateInitializer(context), Timeout(5.seconds)) { _ =>
      val actions = insertAndQueryExampleDataFromTable(context)
      whenReady(context.database.run(actions)) { foundIDs =>
        foundIDs should contain theSameElementsAs List(1)
      }
    }
  }
  it should "be idempotent" in {
    val context = new TestDatabaseContext with DatabaseCommandExecution
    val createTwice = for {
      _ <- SchemaCreateInitializer(context)
      _ <- SchemaCreateInitializer(context)
    } yield ()

    whenReady(createTwice, Timeout(5.seconds)) { _ =>
      val actions = insertAndQueryExampleDataFromTable(context)

      whenReady(context.database.run(actions)) { foundIDs =>
        foundIDs should contain theSameElementsAs List(1)
      }
    }
  }
}
