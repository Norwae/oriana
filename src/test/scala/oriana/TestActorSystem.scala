package oriana

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait TestActorSystem extends BeforeAndAfterAll { self: Suite =>
  private var _system: ActorSystem = _

  implicit def system: ActorSystem = _system
  implicit lazy val ec: ExecutionContext = system.dispatcher
  implicit lazy val mat: Materializer = ActorMaterializer()
  implicit val timeout = Timeout(10.seconds)

  override protected def beforeAll(): Unit =
    _system = ActorSystem("test-" + getClass.getName.filter("abcdefghijklmnopqrstuvwxzyABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(_)))

  override protected def afterAll() = {
    system.terminate()
    super.afterAll()
  }
}
