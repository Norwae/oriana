package oriana

import java.util.UUID

package object testdatabase {

  def createJdbcUrl() = {
    val id = UUID.randomUUID.toString
    s"jdbc:h2:mem:$id;DB_CLOSE_DELAY=-1"
  }

}
