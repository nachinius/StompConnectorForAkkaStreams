
package com.nachinius.akka.stream.stomp.scaladsl

import akka.Done
import akka.stream.scaladsl.Source
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, SendingFrame, SourceStage}

import scala.concurrent.Future

object StompClientSource {

  /**
   * Scala API: Upon materialization this source [[StompClientSource]] connects and subscribes to a topic (set in settings) published in a Stomp server. Each message may be Ack, and handles backpressure.
   */
  def apply(settings: ConnectorSettings): Source[SendingFrame, Future[Done]] =
    Source.fromGraph(new SourceStage(settings))

}
