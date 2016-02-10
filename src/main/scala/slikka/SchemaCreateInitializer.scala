package slikka

import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits.global


object SchemaCreateInitializer extends DBInitializer {
  override def apply(ctx: DatabaseContext) = {
    import ctx.api._
    val tableCreations = ctx.allTables map { table =>
      val query = table.query
      val testTableAccess = for (row <- query) yield 1
      testTableAccess.result.headOption.asTry flatMap {
        case Success(_) => DBIO.successful(())
        case Failure(_) => table.createDDL
      }
    }

    ctx.database.run(DBIO.seq(tableCreations :_*))
  }
}
