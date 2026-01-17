/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.nachinius.akka.stream.stomp.scaladsl

import akka.Done
import akka.stream.scaladsl.Sink
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, SendingFrame, SinkStage}

import scala.concurrent.Future

object StompClientSink {

  /**
   * Scala API: Creates a Sink that connects to a STOMP server and sends messages.
   * 
   * Upon materialization, this sink establishes a connection to the STOMP server specified in the
   * settings. Messages received by the sink are sent to the STOMP destination. The destination
   * can be set in the settings (applied to all messages) or in each individual SendingFrame.
   * If both are set, the per-message destination takes precedence.
   * 
   * When `withAck` is enabled in settings, the sink waits for server acknowledgment before
   * requesting the next message, providing flow control.
   * 
   * The materialized Future[Done] completes when the stream finishes normally or fails if
   * there's a STOMP connection error.
   *
   * @param settings Configuration for the STOMP connection. Destination is optional for sink.
   * @return A Sink accepting SendingFrame messages, materializing to a Future[Done].
   */
  def apply(settings: ConnectorSettings): Sink[SendingFrame, Future[Done]] = Sink.fromGraph(new SinkStage(settings))
}
