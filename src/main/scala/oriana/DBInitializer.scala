package oriana
import oriana.DatabaseActor.InitComplete

import scala.concurrent.Future

/**
  * Initializers prepare the Database on startup, ensuring it is in a usable state once application traffic
  * reaches it. They may be simple (as in, create tables if absent) or elaborate. The init process
  * needs to be complete before the first "user" operation can be attempted.
  */
trait DBInitializer[-InitCtx <: ExecutableDatabaseContext] extends DBOperation[InitCtx, DatabaseActor.InitComplete.type]

object NoInit extends DBInitializer[ExecutableDatabaseContext] {
  override def apply(v1: ExecutableDatabaseContext): Future[DatabaseActor.InitComplete.type] = Future.successful(InitComplete)
}
