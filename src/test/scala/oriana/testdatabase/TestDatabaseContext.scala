package oriana.testdatabase

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import oriana.{BaseDatabaseContext, DatabaseContext}
import slick.driver.H2Driver


class TestDatabaseContext extends BaseDatabaseContext(H2Driver, ConfigFactory.empty().withValue("url", ConfigValueFactory.fromAnyRef(createJdbcUrl())), "") with DatabaseContext {
  val allTables = List(SingleTestTableAccess)
}

