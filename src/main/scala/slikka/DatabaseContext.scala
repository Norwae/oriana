package slikka

import com.typesafe.config.Config
import slick.driver.JdbcProfile

abstract class DatabaseContext(val driver: JdbcProfile, config: Config, path: String) {
  val api = driver.api
  val database = connectToDatabase()

  protected def connectToDatabase(): driver.backend.Database = driver.backend.createDatabase(config, path)

  def allTables: List[driver.api.Table[_]]

}
