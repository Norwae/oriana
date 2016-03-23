package oriana

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRefFactory
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}
import org.reactivestreams.{Subscriber, Subscription}
import slick.dbio.{Effect, NoStream}

class TransactionSubscriber[Context <: DatabaseContext, T](op: (T) => DBTransaction[Context, _, _, _], settings: DBSinkSettings)(implicit actorRefFactory: ActorRefFactory, timeout: Timeout, ec: ExecutionContext, actorName: DatabaseName) extends Subscriber[T] {
  val log = LoggerFactory.getLogger(classOf[TransactionSubscriber[_, _]])
  val total = new AtomicInteger(0)
  val success = new AtomicInteger(0)
  val pending = new AtomicInteger(0)

  @volatile var finished = false
  @volatile var subscription: Subscription = _

  private val promise = Promise[Int]()
  val future = promise.future

  override def onError(t: Throwable) = promise.failure(t)

  override def onSubscribe(s: Subscription) = {
    subscription = s
    s.request(settings.parallelism)
  }

  override def onComplete() = {
    log.info(s"Completed after $total elements, $success successful")
    finished = true

    completePromise()
  }

  override def onNext(t: T) = {
    total.incrementAndGet()
    pending.incrementAndGet()

    executeDBTransaction(op(t)) andThen {
      case Success(_) =>
        success.incrementAndGet()
        pending.decrementAndGet()
        completePromise()
        subscription.request(1)
      case Failure(e) if settings.cancelOnError =>
        subscription.cancel()
        log.error(s"Cancelling stream after $total elements", e)
      case Failure(e) =>
        pending.decrementAndGet()
        completePromise()
        subscription.request(1)
        log.warn(s"Error processing $t, carrying on", e)
    }
  }

  private def completePromise() = {
    if (finished && pending.get() == 0) {
      promise.success(success.get())
    }
  }
}
