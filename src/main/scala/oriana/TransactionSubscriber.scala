package oriana

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRefFactory
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import org.reactivestreams.{Subscription, Subscriber}
import slick.dbio.{Effect, NoStream}

class TransactionSubscriber[Context <: DatabaseContext, T](op: (T) => DBTransaction[Context, Unit, _, _], settings: DBSinkSettings)(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName) extends Subscriber[T] {
  val log = LoggerFactory.getLogger(classOf[TransactionSubscriber[_, _]])
  val nr = new AtomicInteger(0)
  var subscription: Subscription = _

  override def onError(t: Throwable) = ()

  override def onSubscribe(s: Subscription) = {
    subscription = s
    s.request(settings.parallelism)
  }

  override def onComplete() = log.info(s"Completed after $nr elements")

  override def onNext(t: T) = {
    nr.incrementAndGet()

    executeDBTransaction(op(t)) andThen {
      case Success(_) =>
        subscription.request(1)
      case Failure(e) if settings.cancelOnError =>
        subscription.cancel()
        log.error(s"Cancelling stream after $nr elements", e)
      case Failure(e) =>
        subscription.request(1)
        log.warn(s"Error processing $t, carrying on", e)
    }
  }
}
