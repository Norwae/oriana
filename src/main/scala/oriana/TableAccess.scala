package oriana

import slick.dbio.Effect.Schema
import slick.dbio.{DBIOAction, NoStream}
import slick.lifted.TableQuery

/**
  * Defines access to a slick table. This includes a query object (for CRUD operations) and a schema create action.
  * @tparam TableMappingType type of slick FRM table class
  */
trait TableAccess[TableMappingType <: slick.lifted.AbstractTable[_]] {
  /** table query for the given table */
  def query: TableQuery[TableMappingType]
  /** table create schema action */
  def createDDL: DBIOAction[Unit, NoStream, Schema]
}
