package oriana

import akka.actor.Status.Failure
import akka.actor.{Props, Actor, ActorRef}
import org.slf4j.LoggerFactory
import slick.dbio.{Effect, NoStream}

import scala.collection.mutable

class DatabaseActor(dbAccess: ExecutableDatabaseContext) extends Actor {
  import DatabaseActor._
  val log = LoggerFactory.getLogger(classOf[DatabaseActor])

  var schedule: RetrySchedule = DefaultSchedule
  var init: DBInitializer = SchemaCreateInitializer
  val waiting = mutable.Buffer[ActorRef]()

  val changeSchedule: Receive = {
    case schedule: RetrySchedule =>
      this.schedule = schedule
      log.info("Installed new default retry schedule")
  }

  val bufferOperation: Receive = {
    case op: DBOperation[_, _] =>
      waiting += prepareOperation(op, NoRetrySchedule, sender())
      log.debug("queued a non-transactional operation")
    case tr: DBTransaction[_, Any, NoStream, Effect] =>
      val operation: ActorRef = prepareTransaction(tr)
      waiting += operation
      log.debug("queued a transactional operation")
  }

  val ready: Receive = changeSchedule orElse {
    case op: DBOperation[_, _] => prepareOperation(op, NoRetrySchedule, sender()) ! Start(dbAccess)
    case tr: DBTransaction[_, Any, NoStream, Effect] => prepareTransaction(tr) ! Start(dbAccess)
  }

  val initializing: Receive = changeSchedule orElse bufferOperation orElse {
    case InitComplete =>
      log.info(s"Init complete, now starting ${waiting.size} waiting tasks")
      waiting.foreach(_ ! Start(dbAccess))
      waiting.clear()
      context become ready
    case Failure(e) =>
      log.error("Could not initialize the database", e)
      throw e
  }

  val beforeInit: Receive = changeSchedule orElse ({
    case init: DBInitializer =>
      this.init = init
      log.info(s"Set database initializer to an ${init.getClass.getName}")
    case Init =>
      initialize()
      context become initializing
  }: Receive) orElse bufferOperation


  def receive = beforeInit

  protected def prepareTransaction(tr: DBTransaction[_ <: DatabaseContext, Any, NoStream, Effect]): ActorRef = {
    import dbAccess.api._
    val transactionalOperation = (tr.apply _).andThen(_.transactionally).andThen(dbAccess.database.run)
    val retrySchedule = tr.overrideRetrySchedule getOrElse schedule

    prepareOperation(transactionalOperation, retrySchedule, sender())
  }

  protected def prepareOperation(dbOperation: DBOperation[_,_], schedule: RetrySchedule, target: ActorRef) = context.actorOf(DBExecution.props(dbOperation, schedule, target))

  protected def initialize() = prepareOperation(init, NoRetrySchedule, self) ! Start(dbAccess)
}

object DatabaseActor {
  case object Init
  private [oriana] case object InitComplete

  def props(ctx: ExecutableDatabaseContext) = Props(new DatabaseActor(ctx))
}
