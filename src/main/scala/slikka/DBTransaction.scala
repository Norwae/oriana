package slikka

import slick.dbio.{DBIOAction, Effect, NoStream}

trait DBTransaction[Context <: DatabaseContext, +R, +S <: NoStream, -E <: Effect] {
  def apply(context: Context): DBIOAction[R, S, E]
  def overrideRetrySchedule: Option[RetrySchedule] = None
}

object DBTransaction {
  implicit class FunctionalTransaction[Context <: DatabaseContext, +R, +S <: NoStream, -E <: Effect](f: Context => DBIOAction[R,S,E]) {
    def apply(context: Context) = f(context)
  }
}