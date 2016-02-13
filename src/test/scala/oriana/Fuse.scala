package oriana

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{OneForOneStrategy, SupervisorStrategy, Props, Actor}

sealed trait FuseStatus
case object Intact extends FuseStatus
case object Blown extends FuseStatus

class Fuse(childSpec: Props) extends Actor {
  case object FuseBlown
  var status: FuseStatus = Intact
  val child = context.actorOf(childSpec)

  def receive = {
    case Fuse.ReadStatus => sender() ! status
    case FuseBlown => status = Blown
    case x if status == Intact => child forward x
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case _: Throwable =>
      self ! FuseBlown
      Stop
  }
}

object Fuse {
  case object ReadStatus
  def props(childSpec: Props) = Props(new Fuse(childSpec))
}