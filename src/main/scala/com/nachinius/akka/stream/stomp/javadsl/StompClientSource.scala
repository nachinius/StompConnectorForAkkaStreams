/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.nachinius.akka.stream.stomp.javadsl

import java.util.concurrent.CompletionStage

import akka.Done
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, SendingFrame}
import scala.compat.java8.FutureConverters._

object StompClientSource {

  /**
   * Java API: Create a Source [[StompClientSource]] that receives
   * message from a stomp server. It listens to message at the `destination` described in settings.topic. It handles backpressure.
   */
  def create(settings: ConnectorSettings): akka.stream.javadsl.Source[SendingFrame, CompletionStage[Done]] =
    com.nachinius.akka.stream.stomp.scaladsl.StompClientSource(settings).mapMaterializedValue(f => f.toJava).asJava
}
