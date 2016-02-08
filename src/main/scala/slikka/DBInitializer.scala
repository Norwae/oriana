package slikka

trait DBInitializer extends DBOperation[DatabaseContext, DatabaseActor.InitComplete.type]{
  def allowStartupOnFailure = false
  override def retrySchedule = None
}
