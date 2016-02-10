package slikka

import java.nio.file.Files

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, ShouldMatchers}
import slick.ast.ColumnOption.PrimaryKey
import slick.dbio.Effect.{Read, Write}
import slick.driver.H2Driver

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SchemaCreateInitializerSpec extends FlatSpec with ShouldMatchers with ScalaFutures {
  import SchemaCreateInitializerSpec._

  "the schema creating initializer" should "create a  usable schema" in {
    val context = new SchemaCreateContext

    whenReady(SchemaCreateInitializer(context), Timeout(5.seconds)) { _ =>
      val actions = insertAndQueryExampleDataFromTable
      whenReady(context.database.run(actions)) { foundIDs =>
        foundIDs should contain theSameElementsAs List(1)
      }
    }
  }
  it should "be idempotent" in {
    val context = new SchemaCreateContext
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

object SchemaCreateInitializerSpec {

  import H2Driver.api._

  class SingleTestTable(tag: Tag) extends Table[(Int, String)](tag, "single_test") {
    def id = column[Int]("id", PrimaryKey)

    def name = column[String]("name")

    def * = (id, name)
  }

  object SingleTestTableAccess extends TableAccess[SingleTestTable] {
    override def query = TableQuery[SingleTestTable]

    override def createDDL = query.schema.create
  }

  def createJdbcUrl() = {
    val tempDirectory = Files.createTempDirectory("test-db").toAbsolutePath.toString
    s"jdbc:h2:$tempDirectory/test"
  }

  class SchemaCreateContext extends BaseDatabaseContext(H2Driver, ConfigFactory.empty().withValue("url", ConfigValueFactory.fromAnyRef(createJdbcUrl())), "") with DatabaseContext with DatabaseCommandExecution {
    val allTables = List(SingleTestTableAccess)
  }

  val insertAndQueryExampleDataFromTable: DBIOAction[Seq[Int], NoStream, Write with Read] = {
    for {
      _ <- SingleTestTableAccess.query +=(1, "foo")
      id <- SingleTestTableAccess.query.filter(_.name === "foo").map(_.id).result
    } yield id
  }
}