package com.nachinius.akka.stream.stomp.protocol.parboiled

import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand, StompProtocol}
import org.parboiled2._
import Parser.DeliveryScheme.Either



object StompWireStompProtocolWithParboiled {

  def parse(input: ParserInput) = {
    val parser = new StompWireStompProtocolWithParboiled(input)

    val errorOrFrame = for {
      lst <- parser.CommandHeadersAndCursor.run()
      frame = lst.head
      cursor: Int = lst.tail.head.cursor
      body <- getBodyIfShouldHave(frame, input, cursor)
    } yield frame.copy(body = body)

    errorOrFrame match {
      case Left(error) => Left(parser.formatError(error))
      case Right(frame) => Right(frame)
    }
  }

  def getBodyIfShouldHave(frame: DecodedFrame, input: ParserInput, cursor: Int): Either[ParseError, Option[String]] = {
    val command = frame.command
    val lookForBody = Command.mayHaveBody(command)
    val headers = frame.headers
    val next = input.sliceString(cursor, input.length + 1)
    // the first header is the real one
    val mayContentLength = headers.find(_._1 == contentLength).map(_._2)
    (lookForBody, mayContentLength) match {
      case (true, None) =>
        new StompWireStompProtocolWithParboiled(next).BodyWithNullTermination.run().map(Some(_))
      case (true, Some(x)) =>
        val atLeast = x.toInt
        val firstPart = next.slice(0, atLeast + 1)
        new StompWireStompProtocolWithParboiled(next.slice(atLeast + 1, input.length + 1)).BodyWithNullTermination.run().map(firstPart + _).map(Some(_))
      case (false, _) =>
        new StompWireStompProtocolWithParboiled(next).ProperTermination.run().map(_ => None)
    }
  }


  val contentLength = "content-length"

  case class Header(header: String, value: String) {

  }

  case class Command2(value: String) extends AnyVal

  object Command {
    private val mayBody = Set("SEND", "MESSAGE", "ERROR")

    def mayHaveBody(value: String) = mayBody.contains(value)

    def mustNotHaveBody(value: String) = !mayBody.contains(value)
  }

  case class CommandHeaders(command: Command2, headers: Seq[Header]) {
    def toFrame: DecodedFrame = {
      DecodedFrame(
        command.value,
        headers.map(h => (h.header, h.value))
      )
    }
  }

  case class DecodedFrame(command: String, headers: Seq[(String, String)], body: Option[String] = None) {
  }

  case class Cursor(cursor: Int) extends AnyVal

}

class StompWireStompProtocolWithParboiled(val input: ParserInput) extends Parser {

  // SIZE LIMITS that DEPEND ON SERVER
  val maxHeadersPerFrame = 1 << 10
  val maxLengthOfHeaderLine = 1 << 10
  val maxBodySizeInBytes = 1 << 15


  val nullValue = "\u0000"

  def NULL = rule {
    StompProtocol.NULL
  }

  def cr = rule {
    StompProtocol.CR
  }

  def lf = rule {
    StompProtocol.LF
  }

  def colon = rule {
    StompProtocol.COLON
  }

  def escape = rule {
    StompProtocol.ESCAPE
  }

  def comma = rule {
    StompProtocol.COMMA
  }

  def EOL = rule {
    optional(cr) ~ lf
  }

  def CommandAndHeaders = rule {
    command ~ EOL ~
      Headers ~
      EOL ~> ((c, h) => StompWireStompProtocolWithParboiled.CommandHeaders(c, h).toFrame)
  }

  def CommandHeadersAndCursor = rule {
    CommandAndHeaders ~ push(StompWireStompProtocolWithParboiled.Cursor(cursor))
  }

  def ProperTermination = rule {
    NULL ~ EOL.*
  }

  def BodyWithNullTermination = rule {
    capture(noneOf(nullValue).*) ~ ProperTermination
  }

  def command = rule {
    capture(serverCommand | clientCommand) ~> StompWireStompProtocolWithParboiled.Command2
  }

  def clientCommand = rule {
    "SEND" | "SUBSCRIBE" | "UNSUBSCRIBE" | "BEGIN" | "COMMIT" | "ABORT" | "ACK" | "NACK" | "DISCONNECT" | "CONNECT" | "STOMP"
  }

  def serverCommand = rule {
    "CONNECTED" | "MESSAGE" | "RECEIPT" | "ERROR"
  }

  def Headers = rule {
    Header.*
  }

  def Header = rule {
    HeaderName ~ colon ~ HeaderValue ~ EOL ~> StompWireStompProtocolWithParboiled.Header
  }

  def NormalString = rule {
    capture(noneOf(":\r\n").+)
  }

  def HeaderName = rule {
    NormalString
  }

  def HeaderValue = rule {
    NormalString
  }


}