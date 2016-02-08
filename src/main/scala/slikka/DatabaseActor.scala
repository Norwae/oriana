package slikka

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.collection.mutable
import scala.util.Success

class DatabaseActor(dbAccess: DatabaseContext) extends Actor {
  import DatabaseActor._

  var schedule: RetrySchedule = DefaultSchedule
  var initStarted = false
  var init: DBInitializer = SchemaCreateInitializer
  val waiting = mutable.Buffer[ActorRef]()

  val ready: Receive = {
    case op: DBOperation[_, _] => prepareOperation(op, sender()) ! Start(dbAccess)
    case schedule: RetrySchedule => this.schedule = schedule
  }

  val initializing: Receive = {
    case Init if !initStarted=>
      initialize()
      initStarted = true
    case schedule: RetrySchedule => this.schedule = schedule
    case init: DBInitializer => this.init = init
    case op: DBOperation[_,_] =>
      val prepped = prepareOperation(op, sender())
      waiting += prepped
    case InitComplete =>
      waiting.foreach(_ ! Start(dbAccess))
      waiting.clear()
      context.become(ready)
  }

  protected def prepareOperation(dbOperation: DBOperation[_,_], target: ActorRef) = context.actorOf(DBExecution.props(dbOperation, schedule, target))
  protected def initialize() = prepareOperation(init, self) ! Start(dbAccess)

  def receive = initializing
}

object DatabaseActor {
  case object Init
  private [slikka] case object InitComplete
}
