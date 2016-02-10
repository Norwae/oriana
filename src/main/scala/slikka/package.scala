import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.util.Timeout
import slick.dbio.{Effect, NoStream}

import scala.concurrent.{ExecutionContext, Future}

package object slikka {
  type DBOperation[-Context <: DatabaseContext, +Result] = (Context) => Future[Result]

  def executeDBOperation[T: Manifest](op: DBOperation[_ <: DatabaseContext, T], actorName: String = "database")(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/$actorName") ? op).mapTo[T]
  }

  def executeDBTransaction[Context <: DatabaseContext,T: Manifest](op: DBTransaction[Context, T, NoStream, Effect], actorName: String = "database")(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext): Future[T] = {
    (actorRefFactory.actorSelection(s"/user/$actorName") ? op).mapTo[T]
  }
}
