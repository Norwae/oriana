package oriana

import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Simple initializer that executes create DDL for any missing tables. Does not
  * care or have capability to provide an initial data load.
  */
object SchemaCreateInitializer extends DBInitializer {
  /**
    * Tests for each tables existence, and creates it if it is missing
    * @param ctx context
    * @return init complete once all missing tables are created
    */
  override def apply(ctx: ExecutableDatabaseContext) = {
    import ctx.api._
    val tableCreations = ctx.allTables map { table =>
      val query = table.query
      val testTableAccess = for (row <- query) yield 1
      testTableAccess.result.headOption.asTry flatMap {
        case Success(_) => DBIO.successful(())
        case Failure(_) => table.createDDL
      }
    }

    ctx.database.run(DBIO.seq(tableCreations :_*)).map(_ => DatabaseActor.InitComplete)
  }
}
