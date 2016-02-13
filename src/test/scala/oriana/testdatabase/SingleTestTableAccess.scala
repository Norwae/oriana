package oriana
package testdatabase

import slick.lifted.TableQuery
import slick.driver.H2Driver.api._

object SingleTestTableAccess extends TableAccess[SingleTestTable] {
  override def query = TableQuery[SingleTestTable]

  override def createDDL = query.schema.create
}
