package oriana

import akka.actor.ActorSystem
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration._

trait TestActorSystem extends BeforeAndAfterAll { self: Suite =>
  implicit val system: ActorSystem= ActorSystem("test-" + getClass.getName.filter("abcdefghijklmnopqrstuvwxzyABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(_)))
  implicit val timeout = Timeout(1.second)

  override protected def afterAll() = {
    system.terminate()
    super.afterAll()
  }
}
