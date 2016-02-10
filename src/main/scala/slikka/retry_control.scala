package slikka

import scala.reflect.runtime.{universe => ru}

import scala.annotation.StaticAnnotation

class noRetry extends StaticAnnotation

object Retryable {
  private val noRetryType = ru.typeOf[noRetry]
  def unapply(e: Throwable): Option[Throwable] = {
    val mirror = ru.runtimeMirror(e.getClass.getClassLoader)
    val runtimeClass = mirror.reflect(e).symbol.asClass

    if (!runtimeClass.annotations.exists(annotation => annotation.tree.tpe == noRetryType)) None
    else Some(e)
  }
}
