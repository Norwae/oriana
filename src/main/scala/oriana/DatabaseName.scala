package oriana

case class DatabaseName(val name: String) extends AnyVal

object DatabaseName {
  implicit val default = DatabaseName("database")
}