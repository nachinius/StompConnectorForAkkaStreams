/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.nachinius.akka.stream.stomp.client

import java.util

import io.vertx.core.buffer.{Buffer => VertxBuffer}
import io.vertx.ext.stomp.{Frame => VertxFrame}

import scala.collection.JavaConverters._

/**
 * Represents a STOMP message frame with headers and body.
 *
 * @param headers Map of header key-value pairs for the STOMP frame.
 * @param body Message body as a vector of bytes.
 */
case class SendingFrame(headers: Map[String, String], body: Vector[Byte]) {

  def toVertexFrame: VertxFrame = {
    val obj = new VertxFrame().setCommand(VertxFrame.Command.SEND)
    val h: util.Map[String, String] = headers.asJava
    if (!h.isEmpty) {
      obj.setHeaders(h)
    }
    if (body.nonEmpty) {
      obj.setBody(VertxBuffer.buffer(body.toArray))
    }
    obj
  }

  def withDestination(destination: String) =
    copy(headers + ("destination" -> destination))
}

object SendingFrame {
  /**
   * Creates a SendingFrame from a Vert.x STOMP Frame.
   *
   * @param frame The Vert.x frame to convert.
   * @return A SendingFrame with the frame's headers and body.
   */
  def from(frame: VertxFrame): SendingFrame = {
    val body: scala.collection.immutable.Vector[Byte] =
      if (!frame.hasEmptyBody) frame.getBodyAsByteArray.toVector
      else {
        Vector()
      }
    val headers: scala.collection.immutable.Map[String, String] = if (frame.getHeaders == null) {
      Map()
    } else frame.getHeaders.asScala.toMap
    SendingFrame(headers, body)
  }
  
  /**
   * Creates a SendingFrame from a string body.
   *
   * @param bodyStr The string to use as the message body.
   * @return A SendingFrame with the string converted to bytes.
   */
  def from(bodyStr: String): SendingFrame =
    from(bodyStr.getBytes().toVector)
  
  /**
   * Creates a SendingFrame from a byte vector body.
   *
   * @param body The message body as bytes.
   * @return A SendingFrame with no headers and the given body.
   */
  def from(body: Vector[Byte]): SendingFrame =
    SendingFrame(Map(), body)
  
  /**
   * Creates a SendingFrame with a destination and body.
   *
   * @param destination The STOMP destination (topic or queue) for this message.
   * @param body The message body as bytes.
   * @return A SendingFrame with destination header and the given body.
   */
  def from(destination: String, body: Vector[Byte]): SendingFrame =
    from(body).withDestination(destination)
}
