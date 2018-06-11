package com.nachinius.akka.stream.stomp.protocol.parboiled

import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand, StompProtocol}

object ParboiledImpl extends StompProtocol {
  /**
    * Tranform a STOMP's string representation of a message into a `Frame` case class
    * @param input
    * @return
    */
  override def decode(input: String): Either[String, Frame] = StompWireStompProtocolWithParboiled.parse(input).map(
    decodedFrame => Frame(
      StompCommand.fromString(decodedFrame.command),
      decodedFrame.headers.groupBy(_._1).mapValues(_.map(_._2)),
      decodedFrame.body
    )
  )

  /**
    * Transform the case-class Frame into it's STOMP text representation
    * @param frame
    * @return
    */
  override def encode(frame: Frame): Either[String, String] = {
    val command = frame.command.asString + "\n"
    val headers = frame.headers.flatMap({
      case (name: String, values: Seq[String]) => values.map(v => name + ":" + v + "\n")
    }).mkString("") + generateContentLengthHeader(frame) + "\n"
    Right(command + headers + frame.body.getOrElse("") + StompProtocol.NULL)
  }

  private def generateContentLengthHeader(frame: Frame): String = {
    frame.body.fold("")(Frame.Header.contentLength + ":" + _.length.toString + "\n")
  }
}
