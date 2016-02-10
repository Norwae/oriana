package slikka

import java.io.IOException

import org.scalatest.{ShouldMatchers, FlatSpec}

class RetryControlSpec extends FlatSpec with ShouldMatchers {
  class RetryableException extends Exception
  @noRetry class NoRetryException extends Exception

  "the Retryable extractor" should "return None for most exceptions" in {
    Retryable.unapply(new IllegalArgumentException()) shouldEqual None
    Retryable.unapply(new NoSuchElementException()) shouldEqual None
    Retryable.unapply(new IOException()) shouldEqual None
    Retryable.unapply(new RetryableException) shouldEqual None
  }

  it should "return Some(original exception) for annotated classes" in {
    val noRetryInstance = new NoRetryException

    Retryable.unapply(noRetryInstance) shouldEqual Some(noRetryInstance)
  }

}
