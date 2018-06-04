
package com.nachinius.akka.stream.stomp.client

import io.vertx.core.Handler

object VertxStompConversions {
  import scala.language.implicitConversions

  implicit def toHandler[T](x: T => Unit): Handler[T] = new Handler[T] {
    override def handle(event: T): Unit = x(event)
  }
}
