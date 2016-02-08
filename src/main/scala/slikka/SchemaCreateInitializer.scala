package slikka

import scala.concurrent.Future
import scala.util.control.NonFatal

object SchemaCreateInitializer extends DBInitializer {
  override def apply(ctx: DatabaseContext) = {
    import ctx.api._
    val tableCreations = ctx.allTables map { table =>
      val query = TableQuery[table.type]
      val testTableAccess = for (row <- query) yield 1
      ctx.database.run(testTableAccess.result.headOption) recoverWith {
        case NonFatal(e) => ctx.database.run(query.schema.create)
      }
    }

    Future.sequence(tableCreations).map(_ => DatabaseActor.InitComplete)
  }
}
