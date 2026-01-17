/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.nachinius.akka.stream.stomp.scaladsl

import akka.Done
import akka.stream.scaladsl.Source
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, SendingFrame, SourceStage}

import scala.concurrent.Future

object StompClientSource {

  /**
   * Scala API: Creates a Source that connects to a STOMP server and subscribes to a topic or queue.
   * 
   * Upon materialization, this source establishes a connection to the STOMP server specified in the
   * settings and subscribes to the destination topic or queue. The materialized Future[Done] completes
   * when the connection is successfully established and subscription is active.
   * 
   * Messages received from the server are emitted downstream with automatic backpressure handling.
   * When `withAck` is enabled in settings, messages are acknowledged after being pushed downstream.
   * 
   * @param settings Configuration for the STOMP connection. Must include a destination.
   * @return A Source of SendingFrame messages, materializing to a Future[Done] that completes when connected.
   * @throws IllegalArgumentException if settings.destination is None.
   */
  def apply(settings: ConnectorSettings): Source[SendingFrame, Future[Done]] =
    Source.fromGraph(new SourceStage(settings))

}
