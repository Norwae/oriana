package oriana

import slick.dbio.{DBIOAction, Effect, NoStream}

/**
  * Transactions are all-or-nothing sets of operations on the database. They can either succeed (which applies
  * all modifications that may have arisen simultaneously), or fail. If they fail, the oriana actors will
  * attempt the transaction again until its retry allowance is exhausted.
  *
  * Note the absence of the [[DatabaseCommandExecution]]
  * trait in the `Context`, which prevents direct access to the database - and thus allows making the transaction retryable
  *
  * @tparam Context context subclass required by this transaction
  * @tparam R       result type
  */
trait DBTransaction[Context <: DatabaseContext, +R] {
  /**
    * Execute the transaction, yielding a (probably compound) DBIOAction.
    *
    * @param context database context
    * @return compound operation
    */
  def apply(context: Context): DBIOAction[R, NoStream, Effect.Read with Effect.Write]

  /**
    * Allows the transaction to define its own retry schedule. If no schedule is defined (the default), the schedule
    * of the [DatabaseActor] executing the transaction will be used
    * @return overriding schedule, if any
    */
  def overrideRetrySchedule: Option[RetrySchedule] = None
}

/**
  * Companion for the DBTransaction class
  */
object DBTransaction {

  /**
    * Implicit conversion from a function to the transaction type
    * @param f function used for the apply type.
    * @tparam Context context subclass required by this transaction
    * @tparam R       result type
    */

  implicit class FunctionalTransaction[Context <: DatabaseContext, +R](f: Context => DBIOAction[R, NoStream, Effect.Read with Effect.Write]) extends DBTransaction[Context, R] {
    def apply(context: Context) = f(context)
  }

}