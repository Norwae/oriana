package oriana

/**
  * Mix in this trait to an exception to make the exception "fatal", and prevent the normal transaction handling
  * from retrying on this failure. This feature is useful to distinguish "business domain" errors which
  * will never go away from technical glitches and transient conditions (such as a lost DB connection), which a
  * wait-and-retry can eventually defeat.
  */
trait NoRetry