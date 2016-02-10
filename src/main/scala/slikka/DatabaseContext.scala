package slikka

import com.typesafe.config.Config
import slick.driver.JdbcProfile

trait DatabaseContext {
  val driver: JdbcProfile
  val api: driver.API

  def allTables: List[TableAccess[_]]
}

trait DatabaseCommandExecution { self: DatabaseContext =>
  val database: driver.backend.Database
}

abstract class BaseDatabaseContext(val driver: JdbcProfile, config: Config, path: String) {
  val api = driver.api
  val database = connectToDatabase()

  protected def connectToDatabase(): driver.backend.Database = driver.backend.createDatabase(config, path)
}
