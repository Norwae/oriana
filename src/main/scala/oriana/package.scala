import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.Timeout
import slick.dbio.{Streaming, DBIOAction, Effect, NoStream}

import scala.concurrent.{ExecutionContext, Future}

package object oriana {
  /**
    * Context type for operations - mixes in the [DatabaseCommandExecution] trait in to grant access to the database
    * object of the context
    */
  type ExecutableDatabaseContext = DatabaseContext with DatabaseCommandExecution
  /**
    * Type for operations - simple DB interactions that are not part of a transaction. Since they may have multiple, disjoined
    * stages, they can not be retried and can have partial results (depending on which parts of them were executed).
    *
    * @tparam Context context type of the interaction (usually some subclass of `DatabaseContext`
    * @tparam Result type of result produced by the operation
    */
  type DBOperation[-Context <: ExecutableDatabaseContext, +Result] = (Context) => Future[Result]

  type DBStreamOperation[-Context <: DatabaseContext, +Result, -E <: Effect] = (Context) => DBIOAction[Result, Streaming[Result], E]

  /**
    * Syntactic sugar for access to the database. The block argument is scheduled as a [DBOperation] to the implicit
    * database.
    *
    * {{{
    * executeDBOperation { ctx: MyContext with DatabaseCommandExecution =>
    *   // ... generate actions, and execute them with ctx.database.run
    *   ctx.database.run(DBIO.successful(42))
    * }
    * }}}
    *
    * @param op operation to perform
    * @param actorRefFactory most likely an actor system
    * @param timeout timeout for ask operation(s) used under the hood to implement the Future
    * @param ec execution context for ask operation(s) used under the hood to implement the Future
    * @param actorName name for the database actor
    * @tparam T operation return type
    * @return future with operation result
    */
  def executeDBOperation[T: Manifest](op: DBOperation[_ <: ExecutableDatabaseContext, T])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Future[T] = {
    (actorRefFactory.actorSelection(actorName.name) ? op).mapTo[T]
  }

  def executeAsSource[Context <: DatabaseContext, T, E <: Effect](op: DBStreamOperation[Context, T, E])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName) = {
    Source.fromFuture(executeDBOperation { ctx: Context with ExecutableDatabaseContext =>
      val actions = op(ctx)

      Future.successful(ctx.database.stream(actions))
    }) flatMapConcat (Source.fromPublisher(_))
  }
  /**
    * Syntactic sugar for access to the database. The block argument is scheduled as a [DBTransaction] to the implicit
    * database. Transactions may be attempted multiple times in case of failures, making them good candidates
    * for idiom such as optimistic locking.
    *
    *
    * {{{
    * executeDBTransaction { ctx: MyContext =>
    *   // ... generate actions, which will be wrapped with transactionally and executed
    *   DBIO.successful(42)
    * }
    * }}}
    *
    * @param op transaction to attempt
    * @param actorRefFactory most likely an actor system
    * @param timeout timeout for ask operation(s) used under the hood to implement the Future
    * @param ec execution context for ask operation(s) used under the hood to implement the Future
    * @param actorName name for the database actor
    * @tparam Context context type to pass to the transaction
    * @tparam T result type of the transaction
    * @return future with transaction result
    */
  def executeDBTransaction[Context <: DatabaseContext, T: Manifest, S <: NoStream, E <: Effect](op: DBTransaction[Context, T, S, E])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Future[T] = {
    (actorRefFactory.actorSelection(actorName.name) ? op).mapTo[T]
  }

}
