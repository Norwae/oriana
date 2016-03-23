import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.stream.javadsl.GraphDSL
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.Timeout
import slick.dbio.{DBIOAction, Effect, NoStream, Streaming}

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


  /**
    * Models a streamable DB operation. These operations should not modify state. Note the missing [DatabaseCommandExecution] type
    * to prevent direct execution
    * @tparam Context context type
    * @tparam Result result type
    * @tparam E effect type
    */
  type DBStreamOperation[-Context <: DatabaseContext, +Result, -E <: Effect] = (Context) => DBIOAction[_, Streaming[Result], E]

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

  /**
    * Performs a database-using operation as a flow element.
    * @param op operation to execute
    * @param actorRefFactory factory for accessing the lower-level ([executeDBOperation] operation.
    * @param timeout ask timeout for each execution
    * @param ec context to use
    * @param actorName database actor name
    * @tparam Context Context type (useful for table access)
    * @tparam In input type
    * @tparam Out output type
    * @return flow modelling the specified transformation
    */

  def executeAsFlow[Context <: DatabaseContext, In, Out: Manifest](op: In => DBStreamOperation[Context, Out, _])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Flow[In, Out, NotUsed] = {
    Flow[In] map op flatMapConcat (executeAsSource(_))
  }

  def executeAsSink[Context <: DatabaseContext, T](op: T => DBTransaction[Context, _, _, _], settings: DBSinkSettings = DBSinkSettings())(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Sink[T, Future[Int]] = {
    val subscriber: TransactionSubscriber[Context, T] = new TransactionSubscriber(op, settings)
    val flow = Flow.fromSinkAndSource(Sink.fromSubscriber(subscriber), Source.fromFuture(subscriber.future))
    val complete = Sink.head[Int]

    flow.toMat(complete)(Keep.right)
  }

  /**
    * Creates a source from a streamable database operation
    * @param op operation
    * @param actorRefFactory actor factory for communication to the database actor
    * @param timeout ask timeout for the initial creation of the sink, not for each operation
    * @param ec context to use
    * @param actorName database actor name
    * @tparam Context context type (useful for table access)
    * @tparam T emitted type
    * @return source backed by the passed DB operation
    */

  def executeAsSource[Context <: DatabaseContext, T: Manifest](op: DBStreamOperation[Context, T, _])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName) = {
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
  def executeDBTransaction[Context <: DatabaseContext, T: Manifest](op: DBTransaction[Context, T, _, _])(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName): Future[T] = {
    (actorRefFactory.actorSelection(actorName.name) ? op).mapTo[T]
  }

}
