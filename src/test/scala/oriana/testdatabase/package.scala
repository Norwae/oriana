package oriana

import java.nio.file.Files
import java.util.UUID

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import slick.dbio.Effect.{Read, Write}

import scala.concurrent.ExecutionContext.Implicits.global

import slick.driver.H2Driver

package object testdatabase {
  import H2Driver.api._

  def createJdbcUrl() = {
    val id = UUID.randomUUID.toString
    s"jdbc:h2:mem:$id;DB_CLOSE_DELAY=-1"
  }

  val insertAndQueryExampleDataFromTable: DBIOAction[Seq[Int], NoStream, Write with Read] = {
    for {
      _ <- SingleTestTableAccess.query +=(1, "foo")
      id <- SingleTestTableAccess.query.filter(_.name === "foo").map(_.id).result
    } yield id
  }
}
