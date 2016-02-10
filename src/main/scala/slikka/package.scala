import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

package object slikka {
  type DBOperation[-Context <: DatabaseContext, +Result] = (Context) => Future[Result]

  def executeDBOperation[T: Manifest](op: DBOperation[_ <: DatabaseContext, T], actorName: String = "database")(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/$actorName") ? op).mapTo[T]
  }

  def executeDBTransaction[T: Manifest](op: DBTransaction[_ <: DatabaseContext, T, _, _], actorName: String = "database")(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/$actorName") ? op).mapTo[T]
  }
}
