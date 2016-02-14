package oriana

import akka.actor.Status.Failure
import akka.actor.{Props, Actor, ActorRef}
import org.slf4j.LoggerFactory
import slick.dbio.{Effect, NoStream}

import scala.collection.mutable

/**
  * The central point of oriana, an actor that enables an init lifecycle for a database, and
  * a parent to individual operation actors.
  *
  * This actor goes through 3 lifecvcle stages. Uninitialized, where it accepts simple operations and transactions
  * (operations for short), changes to its retry configuration and changes to the initializer. It will queue,
  * but not execute any operations it receives. These queued operations will use the retry configuration that
  * was active when they were first received. Once it received the [DatabaseActor.Init] message, it will
  * evolve to the initializing state.
  *
  * While the actor is initializing, it executes the configured initializer. It will accept operations and keep them
  * queued, and accept changes to the retry configuration. If initialization succeeds, it will enter the ready state.
  * If initialization fails, the failure will trigger an exception on this actor, which will (in turn) lead to
  * the actors supervisor deciding the further fate of the database access. Upon entering the ready state, all
  * previously queued operations are fired
  *
  * In the ready state, the actor accepts retry policy changes and operations, which start executing immediately
  *
  * @param dbAccess database connection
  */
class DatabaseActor(dbAccess: ExecutableDatabaseContext) extends Actor {
  import DatabaseActor._
  private val log = LoggerFactory.getLogger(classOf[DatabaseActor])

  private var schedule: RetrySchedule = DefaultSchedule
  private var init: DBInitializer = SchemaCreateInitializer
  private val waiting = mutable.Buffer[ActorRef]()

  private val changeSchedule: Receive = {
    case schedule: RetrySchedule =>
      this.schedule = schedule
      log.info("Installed new default retry schedule")
  }

  private val bufferOperation: Receive = {
    case op: DBOperation[_, _] =>
      waiting += prepareOperation(op, NoRetrySchedule, sender())
      log.debug("queued a non-transactional operation")
    case tr: DBTransaction[_, Any, NoStream, Effect] =>
      waiting += prepareTransaction(tr)
      log.debug("queued a transactional operation")
  }

  private val ready: Receive = changeSchedule orElse {
    case op: DBOperation[_, _] => prepareOperation(op, NoRetrySchedule, sender()) ! Start(dbAccess)
    case tr: DBTransaction[_, Any, NoStream, Effect] => prepareTransaction(tr) ! Start(dbAccess)
  }

  private val initializing: Receive = changeSchedule orElse bufferOperation orElse {
    case InitComplete =>
      log.info(s"Init complete, now starting ${waiting.size} waiting tasks")
      waiting.foreach(_ ! Start(dbAccess))
      waiting.clear()
      context become ready
    case Failure(e) =>
      log.error("Could not initialize the database", e)
      throw e
  }

  private val beforeInit: Receive = changeSchedule orElse ({
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
  case object InitComplete

  def props(ctx: ExecutableDatabaseContext) = Props(new DatabaseActor(ctx))
}
