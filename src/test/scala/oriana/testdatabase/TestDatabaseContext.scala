package oriana.testdatabase

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import oriana.{SimpleDatabaseContext, DatabaseContext}
import slick.driver.H2Driver


class TestDatabaseContext extends SimpleDatabaseContext(H2Driver, ConfigFactory.empty().withValue("url", ConfigValueFactory.fromAnyRef(createJdbcUrl()))) with DatabaseContext {
  val allTables = List(SingleTestTableAccess)
}

