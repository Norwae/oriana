package slikka

import akka.actor.{Actor, ActorRef}

import scala.collection.mutable

class DatabaseActor(dbAccess: DatabaseContext) extends Actor {
  import DatabaseActor._

  var schedule: RetrySchedule = DefaultSchedule
  var init: DBInitializer = SchemaCreateInitializer
  val waiting = mutable.Buffer[ActorRef]()

  val changeSchedule: Receive = {
    case schedule: RetrySchedule => this.schedule = schedule
  }

  val bufferOperation: Receive = {
    case op: DBOperation[_,_] =>
      val prepped = prepareOperation(op, sender())
      waiting += prepped
  }

  val ready: Receive = changeSchedule orElse {
    case op: DBOperation[_, _] => prepareOperation(op, sender()) ! Start(dbAccess)
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

  protected def prepareOperation(dbOperation: DBOperation[_,_], target: ActorRef) = context.actorOf(DBExecution.props(dbOperation, dbOperation.retrySchedule getOrElse schedule, target))

  protected def initialize() = prepareOperation(init, self) ! Start(dbAccess)
}

object DatabaseActor {
  case object Init
  private [slikka] case object InitComplete
}
