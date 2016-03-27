package oriana

case class DBSinkSettings(cancelOnError: Throwable => Boolean = _ => true, parallelism: Int = 1) {
  require(parallelism >= 1)
}
