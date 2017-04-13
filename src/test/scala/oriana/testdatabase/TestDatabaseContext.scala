package oriana.testdatabase

import com.typesafe.config.ConfigFactory
import oriana.{SimpleDatabaseContext, TableAccess}
import slick.lifted.TableQuery

import scala.collection.JavaConverters._

class TestDatabaseContext extends SimpleDatabaseContext(ConfigFactory.parseMap(Map("url" -> createJdbcUrl(), "type" -> "H2").asJava)) {
  val table = TableQuery[SingleTestTable]
  val allTables: List[TableAccess[_]] = List(table)

}


