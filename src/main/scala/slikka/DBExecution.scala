package slikka

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Status.{Failure => FailureStatus}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class DBExecution[DBContext <: ExecutableDatabaseContext, T](operation: DBOperation[DBContext, T], retrySchedule: RetrySchedule, target: ActorRef) extends Actor {
  var retryCount = 0
  val exceptions = mutable.Buffer[Throwable]()

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
  def props[DBContext <: ExecutableDatabaseContext, T](op: DBOperation[DBContext, T], schedule: RetrySchedule, target: ActorRef) = Props(new DBExecution[DBContext, T](op, schedule, target))
}