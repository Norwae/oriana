package oriana.testdatabase

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import oriana.{DatabaseCommandExecution, DatabaseContext, BaseDatabaseContext}
import slick.driver.H2Driver


class TestDatabaseContext extends BaseDatabaseContext(H2Driver, ConfigFactory.empty().withValue("url", ConfigValueFactory.fromAnyRef(createJdbcUrl())), "") with DatabaseContext with DatabaseCommandExecution {
  val allTables = List(SingleTestTableAccess)
}

