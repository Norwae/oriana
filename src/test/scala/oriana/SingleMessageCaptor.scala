package oriana

import akka.actor.{ActorRef, Actor}

import scala.collection.mutable

class SingleMessageCaptor extends Actor {
  import oriana.SingleMessageCaptor.Read
  var contents: Any = _
  var waiting = mutable.Buffer[ActorRef]()

  val resultReceived: Receive = {
    case Read => sender() ! contents
  }

  val waitForResult: Receive = {
    case Read => waiting += sender()
    case x =>
      contents = x
      waiting.foreach(_ ! x)
      waiting.clear()

      context.become(resultReceived)
  }


  def receive = waitForResult
}

object SingleMessageCaptor {
  case object Read
}
