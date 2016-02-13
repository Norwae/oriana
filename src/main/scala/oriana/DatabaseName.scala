package oriana

/**
  * Wrapper type representing a database name. The name is supposed to be the
  * actor name of the database actor.
  * @param name name of the actor
  */
case class DatabaseName(val name: String) extends AnyVal

object DatabaseName {
  /**
    * The default database - named (unimaginatively enough) "database"
    */
  implicit val default = DatabaseName("database")
}