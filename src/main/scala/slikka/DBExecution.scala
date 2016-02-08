package slikka

import akka.actor.{ActorRef, Actor}


import scala.collection.mutable
import scala.util.{Success, Failure}
import scala.util.control.NonFatal

import scala.concurrent.ExecutionContext.Implicits.global

class DBExecution[DBContext <: DatabaseContext : Manifest, T](operation: DBOperation[DBContext, T], retrySchedule: RetrySchedule) extends Actor {
  var retryCount = 0
  val exceptions = mutable.Buffer[Throwable]()

  override def receive = {
    case Start(ctx) =>
      val target = sender()
      operation(ctx.asInstanceOf[DBContext]) onComplete {
        case fail@Failure(Retryable(NonFatal(e))) =>
          retrySchedule.retryDelay(retryCount) match {
            case Some(delay) =>
              exceptions += e
              retryCount += 1
              context.system.scheduler.scheduleOnce(delay, self, Start(ctx))
            case None =>
              exceptions.foreach(e.addSuppressed)
              reportCalculationResult(target, fail)
          }
        case x: Failure[T] => reportCalculationResult(target, x)
        case Success(value) => reportCalculationResult(target, value)
      }
  }

  private def reportCalculationResult(target: ActorRef, value: Any): Unit = {
    target ! value
    context.stop(self)
  }
}
