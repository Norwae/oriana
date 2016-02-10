package slikka

import akka.actor.{Props, ActorRef, Actor}


import scala.collection.mutable
import scala.util.{Success, Failure}
import scala.util.control.NonFatal

import scala.concurrent.ExecutionContext.Implicits.global

class DBExecution[DBContext <: ExecutableDatabaseContext, T](operation: DBOperation[DBContext, T], retrySchedule: RetrySchedule, target: ActorRef) extends Actor {
  var retryCount = 0
  val exceptions = mutable.Buffer[Throwable]()

  override def receive = {
    case Start(ctx) =>
      operation(ctx.asInstanceOf[DBContext]) onComplete {
        case fail@Failure(Retryable(NonFatal(e))) =>
          retrySchedule.retryDelay(retryCount) match {
            case Some(delay) =>
              exceptions += e
              retryCount += 1
              context.system.scheduler.scheduleOnce(delay, self, Start(ctx))
            case None =>
              exceptions.foreach(e.addSuppressed)
              reportCalculationResult(fail)
          }
        case x: Failure[T] => reportCalculationResult(x)
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