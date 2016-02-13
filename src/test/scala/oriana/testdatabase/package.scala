package oriana

import java.nio.file.Files

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import slick.dbio.Effect.{Read, Write}

import scala.concurrent.ExecutionContext.Implicits.global

import slick.driver.H2Driver

package object testdatabase {
  import H2Driver.api._

  def createJdbcUrl() = {
    val tempDirectory = Files.createTempDirectory("test-db").toAbsolutePath.toString
    s"jdbc:h2:$tempDirectory/test"
  }

  val insertAndQueryExampleDataFromTable: DBIOAction[Seq[Int], NoStream, Write with Read] = {
    for {
      _ <- SingleTestTableAccess.query +=(1, "foo")
      id <- SingleTestTableAccess.query.filter(_.name === "foo").map(_.id).result
    } yield id
  }
}
