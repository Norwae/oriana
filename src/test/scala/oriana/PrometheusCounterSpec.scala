package oriana

import org.scalatest.{FlatSpec, Matchers}

class PrometheusCounterSpec extends FlatSpec with Matchers {

  case class CounterValues(timeouts: Int, pending: Int, failed: Int, success: Int, retry: Int)

  def snapshot() = CounterValues(
    PrometheusMonitor.timeouts.get().toInt,
    PrometheusMonitor.pending.get().toInt,
    PrometheusMonitor.failure.get().toInt,
    PrometheusMonitor.success.get().toInt,
    PrometheusMonitor.retries.get().toInt
  )

  "The prometheus counter" should "count timeouts" in {
    val prev = snapshot()
    PrometheusMonitor.executeTimeout()
    val next = snapshot()

    prev.timeouts + 1 shouldEqual next.timeouts
    prev.pending shouldEqual next.pending
    prev.failed shouldEqual next.failed
    prev.success shouldEqual next.success
    prev.retry shouldEqual next.retry
  }

  it should "increment pending on pending" in {
    val prev = snapshot()
    PrometheusMonitor.operationPending()
    val next = snapshot()

    prev.timeouts shouldEqual next.timeouts
    prev.pending + 1 shouldEqual next.pending
    prev.failed shouldEqual next.failed
    prev.success shouldEqual next.success
    prev.retry shouldEqual next.retry
  }

  it should "decrement pending on success" in {
    val prev = snapshot()
    PrometheusMonitor.operationSucceeded()
    val next = snapshot()

    prev.timeouts shouldEqual next.timeouts
    prev.pending - 1  shouldEqual next.pending
    prev.failed shouldEqual next.failed
    prev.success + 1 shouldEqual next.success
    prev.retry shouldEqual next.retry
  }

  it should "decrement pending on failure" in {
    val prev = snapshot()
    PrometheusMonitor.operationFailed()
    val next = snapshot()

    prev.timeouts shouldEqual next.timeouts
    prev.pending - 1 shouldEqual next.pending
    prev.failed + 1 shouldEqual next.failed
    prev.success shouldEqual next.success
    prev.retry shouldEqual next.retry
  }

  it should "count retries without affecting pending" in {
    val prev = snapshot()
    PrometheusMonitor.operationRetry()
    val next = snapshot()

    prev.timeouts shouldEqual next.timeouts
    prev.pending shouldEqual next.pending
    prev.failed shouldEqual next.failed
    prev.success shouldEqual next.success
    prev.retry + 1 shouldEqual next.retry
  }
}
