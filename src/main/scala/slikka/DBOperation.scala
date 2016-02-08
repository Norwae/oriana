package slikka

import scala.concurrent.Future

trait DBOperation[-Context <: DatabaseContext, +T] {
  def apply(ctx: Context): Future[T]
  def retrySchedule: Option[RetrySchedule]
}

object DBOperation {
  private class FunctionDBOp[-Context <: DatabaseContext, +T](f: (Context) => Future[T], val retrySchedule: Option[RetrySchedule]) extends DBOperation[Context, T] {
    def apply(ctx: Context): Future[T] = f(ctx)
  }

  def apply[Context <: DatabaseContext, T](f: (Context) => Future[T]): DBOperation[Context, T] = new FunctionDBOp[Context, T](f, None)
  def runScheduled[Context <: DatabaseContext, T](f: (Context) => Future[T], schedule: RetrySchedule): DBOperation[Context, T] = new FunctionDBOp[Context, T](f, Some(schedule))

}