package oriana

case class DBSinkSettings(cancelOnError: Boolean = true, parallelism: Int = 1) {
  require(parallelism >= 1)
}
