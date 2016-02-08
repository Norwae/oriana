package slikka

import scala.reflect.runtime.{universe => ru}

import scala.annotation.StaticAnnotation

class noRetry extends StaticAnnotation

object Retryable {
  def unapply(e: Throwable): Option[Throwable] = {
    val mirror = ru.runtimeMirror(classOf[noRetry].getClassLoader)
    val runtimeClass = mirror.reflect(e).symbol.asClass

    if (!runtimeClass.annotations.exists(annotation => annotation.isInstanceOf[noRetry])) None
    else Some(e)
  }
}
