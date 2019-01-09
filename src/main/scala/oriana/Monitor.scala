package oriana

import io.prometheus.client.{CollectorRegistry, Counter, Gauge}

/**
  * A monitor is used to observe some relevant events in the database layer. These
  * events are success or failure of operations, the number of open (pending) operations
  * and if the execute* family of functions produced ask timeouts.
  *
  * The monitor is configured by sending it to the database actor. The actor switches
  * the monitor, and applies the new monitor to all subsequent (but not currently running)
  * operations. An operation is thus only ever observed by one monitor.
  *
  * The executeTimeout method is an exception to this rule, since it is not tied to an
  * actual operation, but to the execute* wrapper methods (most prominently
  * [[oriana.executeDBOperation]] and [[oriana.executeDBTransaction()]]).
  */
trait Monitor {
  /** An `execute*` family wrapper encountered an ask timeout */
  def executeTimeout(): Unit

  /** An operation has started but not produced a final result */
  def operationPending(): Unit
  /** An operation has produced a final successful result */
  def operationSucceeded(): Unit
  /** An operation has failed, but scheduled a retry */
  def operationRetry(): Unit
  /** An operation has produced a final failed result */
  def operationFailed(): Unit
}

/**
  * Default (NOP) implementation of a monitor
  */
object NopMonitor extends Monitor {
  override def executeTimeout(): Unit = ()
  override def operationPending(): Unit = ()
  override def operationSucceeded(): Unit = ()
  override def operationRetry(): Unit = ()
  override def operationFailed(): Unit = ()
}

/**
  * If the optional prometheus dependency is provided, this monitor exports 5 metrics to prometheus:
  *
  * - oriana_pending (gauge), number of operations currently executing (includes those waiting for a retry)
  * - oriana_timeout (counter), number of execute* family functions terminated due to an ask timeout
  * - oriana_retry (counter) number of retries scheduled
  * - oriana_success (counter) number of successful operations
  * - oriana_failure (counter) number of failed operations
  */
object PrometheusMonitor extends Monitor {
  private [oriana] val pending = Gauge.build("oriana_pending", "Number of currently pending operations").create()
  private [oriana] val timeouts = Counter.build("oriana_timeout", "Timeouts encountered in execute* family functions").create()

  private [oriana] val retries = Counter.build("oriana_retry", "Retries attempted for db transactions").create()
  private [oriana] val success = Counter.build("oriana_success", "Successful DB operations").create()
  private [oriana] val failure = Counter.build("oriana_failure", "Failed DB operations").create()


  /**
    * Register the metrics
    * @param registry registry to register with (default value: The default registry)
    */
  def register(registry: CollectorRegistry = CollectorRegistry.defaultRegistry): Unit = {
    pending.register(registry)
    timeouts.register(registry)
    retries.register(registry)
    success.register(registry)
    failure.register(registry)
  }

  override def executeTimeout(): Unit = timeouts.inc()

  override def operationPending(): Unit = pending.inc()

  override def operationSucceeded(): Unit = {
    pending.dec()
    success.inc()
  }

  override def operationRetry(): Unit = retries.inc()

  override def operationFailed(): Unit = {
    pending.dec()
    failure.inc()
  }
}