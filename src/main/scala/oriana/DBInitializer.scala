package oriana

trait DBInitializer extends DBOperation[ExecutableDatabaseContext, DatabaseActor.InitComplete.type]
