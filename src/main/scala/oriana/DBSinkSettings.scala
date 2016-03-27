package oriana

/**
  * Options modifying the behaviour of a database sink
  * @param cancelOnError should the sink cancel the stream on an error? The default will always cancel
  * @param parallelism limit of parallel items to process. Default is single-transaction
  */
case class DBSinkSettings(cancelOnError: Throwable => Boolean = _ => true, parallelism: Int = 1) {
  require(parallelism >= 1)
}
