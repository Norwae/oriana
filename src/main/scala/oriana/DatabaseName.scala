package oriana

import akka.actor.{ActorPath, ActorRef}

/**
  * Wrapper type representing a database name. The name is supposed to be the
  * actor name of the database actor.
  *
  * @param name name of the actor
  */
case class DatabaseName(val name: String) extends AnyVal

object DatabaseName {
  /**
    * The default database - named (unimaginatively enough) "database"
    */
  implicit val default = DatabaseName("/user/database")

  /**
    * Derives an database name from an actor path
    * @param path actor path
    * @return path, as a database name
    */
  def apply(path: ActorPath): DatabaseName = DatabaseName(path.toStringWithoutAddress)
}