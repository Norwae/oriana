package slikka

import slick.dbio.Effect.Schema
import slick.dbio.{DBIOAction, NoStream}
import slick.lifted.TableQuery


trait TableAccess[TableMappingType <: slick.lifted.AbstractTable[_]] {
  def query: TableQuery[TableMappingType]
  def createDDL: DBIOAction[Unit, NoStream, Schema]
}
