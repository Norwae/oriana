package oriana

import com.typesafe.config.Config
import slick.driver._

/**
  * Database context encapsulate a driver, its api and a list of all tables. You are encouraged to add members
  * for your tables when you implement your class. For example:
  *
  * {{{
  *   class MyContext extends DatabaseContext {
  *     val driver = MySQLDriver
  *     val api = driver.api
  *
  *     val ponies: TableAccess[Pony] = new PonyTableAccess(driver)
  *     val riders: TableAccess[Rider] = new RiderTableAccess(driver)
  *     val ponyReviews: TableAccess[PonyReview] = new PonyReviewTableAccess(driver)
  *
  *     val allTables = List(ponies, riders, ponyReviews)
  *   }
  * }}}
  */
trait DatabaseContext {
  val driver: JdbcProfile
  val api: driver.API

  /**
    * All tables relevant in this context. Can be queried by the [[DBInitializer]] implementation to aid startup
    *
    * @return all tables
    */
  def allTables: List[TableAccess[_]]
}

/**
  * Marks a context as supporting direct execution. Contexts with direct execution can be used for "simple" DBOperations,
  * but because transactionality cannot be guaranteed, will not be subject to retries.
  */
trait DatabaseCommandExecution { self: DatabaseContext =>
  val database: driver.backend.Database
}

/**
  * A base class for Contexts that derive their database connection from a typesafe configuration. The driver object
  * still needs to be determined "by hand", or by calling the alternate constructor, which offers a way to determine it
  * by configuration
 *
  * @param driver driver object to use
  * @param config configuration of the database. Project it down to the required sub-configuration before passing
  */
class SimpleDatabaseContext(val driver: JdbcProfile, config: Config) {
  /**
    * Alternate constructor for deriving the driver from the configuration.
    *
    * {{{
      config.getString("driver") match {
        case "H2" => H2Driver
        case "MySQL" => MySQLDriver
        case "Postgres" => PostgresDriver
        case "Derby" => DerbyDriver
        case "Hsqldb" => HsqldbDriver
        case "SQLite" => SQLiteDriver
      }
    * }}}
    *
    *
    * @param config configuration (projected to the correct key)
    * @return new instance
    */
  def this(config: Config) = this({
    config.getString("type") match {
      case "H2" => H2Driver
      case "MySQL" => MySQLDriver
      case "Postgres" => PostgresDriver
      case "Derby" => DerbyDriver
      case "Hsqldb" => HsqldbDriver
      case "SQLite" => SQLiteDriver
    }
  }, config)
  val api = driver.api
  val database = connectToDatabase()

  protected def connectToDatabase(): driver.backend.Database = driver.backend.createDatabase(config, "")
}
