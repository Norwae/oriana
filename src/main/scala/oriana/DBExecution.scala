package oriana

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Status.{Failure => FailureStatus}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Internal implementation actor for a single database interaction. This actor handles the actual invocation of
  * an operation, including failure / retry logic.
  * @param operation operation to perform
  * @param retrySchedule retry schedule for failed operations
  * @param target actor to send result or error report to
  * @tparam DBContext context type
  * @tparam T result type
  */
class DBExecution[DBContext <: ExecutableDatabaseContext, T](operation: DBOperation[DBContext, T], retrySchedule: RetrySchedule, target: ActorRef) extends Actor {
  private var retryCount = 0
  private val exceptions = mutable.Buffer[Throwable]()

  override def receive = {
    case Start(ctx) =>
      operation(ctx.asInstanceOf[DBContext]) onComplete {
        case Failure(e: NoRetry) =>
          exceptions.foreach(e.addSuppressed)
          reportCalculationResult(FailureStatus(e))
        case Failure(e) =>
          retrySchedule.retryDelay(retryCount) match {
            case Some(delay) =>
              exceptions += e
              retryCount += 1
              context.system.scheduler.scheduleOnce(delay, self, Start(ctx))
            case None =>
              exceptions.foreach(e.addSuppressed)
              reportCalculationResult(FailureStatus(e))
          }
        case Success(value) => reportCalculationResult(value)
      }
  }

  private def reportCalculationResult(value: Any): Unit = {
    target ! value
    context.stop(self)
  }
}

object DBExecution {
  /**
    * Generates a new DBExecution properties object
    * @param op operation for the props
    * @param schedule retry schedule
    * @param target target actor ref
    * @tparam DBContext context type
    * @tparam T result type
    * @return props for the given set of parameters
    */
  def props[DBContext <: ExecutableDatabaseContext, T](op: DBOperation[DBContext, T], schedule: RetrySchedule, target: ActorRef) = Props(new DBExecution[DBContext, T](op, schedule, target))
}