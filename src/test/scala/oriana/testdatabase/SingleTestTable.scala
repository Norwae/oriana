package oriana
package testdatabase

import slick.ast.ColumnOption.PrimaryKey
import slick.driver.H2Driver.api._

class SingleTestTable(tag: Tag) extends Table[(Int, String)](tag, "single_test") {
  def id = column[Int]("id", PrimaryKey)

  def name = column[String]("name")

  def * = (id, name)
}


