package oriana.testdatabase

import com.typesafe.config.ConfigFactory
import oriana.{DatabaseContext, SimpleDatabaseContext}

import scala.collection.JavaConversions._

class TestDatabaseContext extends SimpleDatabaseContext(ConfigFactory.parseMap(Map("url" -> createJdbcUrl(), "type" -> "H2"))) with DatabaseContext {
  val allTables = List(SingleTestTableAccess)
}

