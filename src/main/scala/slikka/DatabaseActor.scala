package slikka

import akka.actor.{Props, Actor, ActorRef}
import com.typesafe.config.Config

import scala.collection.mutable

class DatabaseActor(dbAccess: ExecutableDatabaseContext) extends Actor {
  import DatabaseActor._

  var schedule: RetrySchedule = DefaultSchedule
  var init: DBInitializer = SchemaCreateInitializer
  val waiting = mutable.Buffer[ActorRef]()

  val changeSchedule: Receive = {
    case schedule: RetrySchedule => this.schedule = schedule
  }

  val bufferOperation: Receive = {
    case op: DBOperation[_, _] =>
      val prepped = prepareOperation(op, NoRetrySchedule, sender())
      waiting += prepped
    case tr: DBTransaction[_, _, _, _] =>
      import dbAccess.api._
      val transactionalOperation = (tr.apply _).andThen(_.transactionally).andThen(dbAccess.database.run)
      val retrySchedule = tr.overrideRetrySchedule getOrElse schedule

      prepareOperation(transactionalOperation, schedule, sender())
  }

  val ready: Receive = changeSchedule orElse {
    case op: DBOperation[_, _] => prepareOperation(op, NoRetrySchedule, sender()) ! Start(dbAccess)
  }

  val initializing: Receive = changeSchedule orElse bufferOperation orElse {
    case InitComplete =>
      waiting.foreach(_ ! Start(dbAccess))
      waiting.clear()
      context become ready
  }

  val beforeInit: Receive = changeSchedule orElse bufferOperation orElse {
    case init: DBInitializer => this.init = init
    case Init =>
      initialize()
      context become initializing
  }


  def receive = beforeInit

  protected def prepareOperation(dbOperation: DBOperation[_,_], schedule: RetrySchedule, target: ActorRef) =
    context.actorOf(DBExecution.props(dbOperation, schedule, target))

  protected def initialize() = prepareOperation(init, NoRetrySchedule, self) ! Start(dbAccess)
}

object DatabaseActor {
  case object Init
  private [slikka] case object InitComplete

  def props(ctx: ExecutableDatabaseContext) = Props(new DatabaseActor(ctx))
}
