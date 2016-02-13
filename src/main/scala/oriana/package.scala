import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.util.Timeout
import slick.dbio.{Effect, NoStream}

import scala.concurrent.{ExecutionContext, Future}

package object oriana {
  type ExecutableDatabaseContext = DatabaseContext with DatabaseCommandExecution
  type DBOperation[-Context <: ExecutableDatabaseContext, +Result] = (Context) => Future[Result]

  def executeDBOperation[T: Manifest](op: DBOperation[_ <: ExecutableDatabaseContext, T])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/${actorName.name}") ? op).mapTo[T]
  }

  def executeDBTransaction[Context <: DatabaseContext, T: Manifest](op: DBTransaction[Context, T, NoStream, Effect])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/${actorName.name}") ? op).mapTo[T]
  }
}
