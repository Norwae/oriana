package slikka

import slick.dbio.Effect.Schema
import slick.dbio.{DBIOAction, NoStream}
import slick.lifted.{AbstractTable, TableQuery}


trait Entity[EntityType, Table <: AbstractTable[EntityType]] {
  def query: TableQuery[Table]
  def createDDL: DBIOAction[Unit, NoStream, Schema]
}
