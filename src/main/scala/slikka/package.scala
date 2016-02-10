import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

package object slikka {
  type DBOperation[-Context <: DatabaseContext, +Result] = (Context) => Future[Result]

  def runInDBContext[T: Manifest](op: DBOperation[_ <: DatabaseContext, T], actorName: String = "database")(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/$actorName") ? op).mapTo[T]
  }
}
