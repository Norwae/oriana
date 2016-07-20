package oriana

/**
  * Initializers prepare the Database on startup, ensuring it is in a usable state once application traffic
  * reaches it. They may be simple (as in, create tables if absent) or elaborate. The init process
  * needs to be complete before the first "user" operation can be attempted.
  */
trait DBInitializer[-InitCtx <: ExecutableDatabaseContext] extends DBOperation[InitCtx, DatabaseActor.InitComplete.type]
