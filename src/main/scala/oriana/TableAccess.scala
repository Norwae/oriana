package oriana

import slick.dbio.Effect.Schema
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import slick.lifted._

/**
  * Defines access to a slick table. This includes a query object (for CRUD operations) and a schema create action.
  * @tparam TableMappingType type of slick FRM table class
  */
trait TableAccess[TableMappingType <: AbstractTable[_]] {
  /** table query for the given table */
  def query: TableQuery[TableMappingType]
  /** table create schema action */
  def createDDL: DBIOAction[Unit, NoStream, Schema]
}

object TableAccess {
  implicit class SimpleTableAccess[T <: AbstractTable[_]](val query: TableQuery[T])(implicit driver: JdbcProfile) extends TableAccess[T]{
    /** table create schema action */
    override def createDDL: DBIOAction[Unit, NoStream, Schema] = {
      val schema = driver.buildTableSchemaDescription(query.baseTableRow.asInstanceOf[driver.Table[_]])
      driver.createSchemaActionExtensionMethods(schema).create
    }
  }
}