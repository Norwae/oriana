package oriana
import oriana.DatabaseActor.InitComplete

import scala.concurrent.Future

/**
  * A pseudo-initializer of schema management is not desired.
  */
object ExternalInitializer extends DBInitializer[ExecutableDatabaseContext] {
  override def apply(v1: ExecutableDatabaseContext): Future[InitComplete.type] = Future.successful(InitComplete)
}
