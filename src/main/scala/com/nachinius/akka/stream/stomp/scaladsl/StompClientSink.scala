
package com.nachinius.akka.stream.stomp.scaladsl

import akka.Done
import akka.stream.scaladsl.Sink
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, SendingFrame, SinkStage}

import scala.concurrent.Future

object StompClientSink {

  /**
   * Scala API: Connects to a STOMP server upon materialization and sends incoming messages to the server.
   * Each materialized sink will create one connection to the broker. This stage sends messages to the destination
   * named in the settings options, if present, instead of the one written in the incoming message to the Sink.
   *
   * This stage materializes to a Future[Done], which can be used to know when the Sink completes, either normally
   * or because of a stomp failure.
   */
  def apply(settings: ConnectorSettings): Sink[SendingFrame, Future[Done]] = Sink.fromGraph(new SinkStage(settings))
}
