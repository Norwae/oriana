package oriana



import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, ShouldMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SchemaCreateInitializerSpec extends FlatSpec with ShouldMatchers with ScalaFutures {
  import testdatabase._

  "the schema creating initializer" should "create a  usable schema" in {
    val context = new TestDatabaseContext with DatabaseCommandExecution

    whenReady(SchemaCreateInitializer(context), Timeout(5.seconds)) { _ =>
      val actions = insertAndQueryExampleDataFromTable
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
      val actions = insertAndQueryExampleDataFromTable

      whenReady(context.database.run(actions)) { foundIDs =>
        foundIDs should contain theSameElementsAs List(1)
      }
    }
  }
}
