package slikka

trait DBInitializer extends DBOperation[DatabaseContext, DatabaseActor.InitComplete.type]{
  final override def retrySchedule = Some(NoRetrySchedule)
}
