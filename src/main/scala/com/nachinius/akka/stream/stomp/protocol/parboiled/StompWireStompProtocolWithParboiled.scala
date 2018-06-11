package com.nachinius.akka.stream.stomp.protocol.parboiled

import com.nachinius.akka.stream.stomp.protocol.{Frame, StompProtocol}
import org.parboiled2.Parser.DeliveryScheme.Either
import org.parboiled2._
import shapeless.HNil


object StompWireStompProtocolWithParboiled {

  def parse(input: ParserInput): Either[String, DecodedFrame] = {
    val headerAndCursor: Either[String, (DecodedFrame, Int)] = parseTopWithCursor(input)

    headerAndCursor.flatMap({
      case (frame: DecodedFrame, cursor: Int) =>
        val maybodyOrError = getBodyIfShouldHave(frame, input, cursor)
        maybodyOrError.map( optBody => frame.copy(body = optBody))
    })
  }

  private def parseTopWithCursor(input: ParserInput): Either[String, (DecodedFrame, Int)] = {
    val parser = new StompWireStompProtocolWithParboiled(input)
    val headerAndCursor = parser.CommandHeadersAndCursor.run()

    headerAndCursor match {
      case Left(error) => Left(parser.formatError(error))
      case Right(z) => Right(z.head, z.tail.head.cursor)
    }
  }

  private def getBodyIfShouldHave(frame: DecodedFrame, input: ParserInput, cursor: Int): Either[String, Option[String]] = {
    val command = frame.command
    val lookForBody = Command.mayHaveBody(command)
    val headers = frame.headers
    val next = input.sliceString(cursor, input.length)
    // the first header is the real one
    val mayContentLength = headers.find(_._1 == Frame.Header.contentLength).map(_._2)
    (lookForBody, mayContentLength) match {
      case (true, None) =>
        // body is ended by NULL
        val parboiled = new StompWireStompProtocolWithParboiled(next)
        val resultOrError = parboiled.BodyWithNullTermination.run()
        resultOrError match {
          case Left(error) => Left(parboiled.formatError(error))
          case Right(result) => Right(Some(result))
        }
      case (true, Some(x)) =>
        // body length is at least 'x'
        val atLeast = x.toInt
        val (firstPart, hereNext) = next.splitAt(atLeast)
        val parboiled = new StompWireStompProtocolWithParboiled(hereNext)
        val resultOrError = parboiled.BodyWithNullTermination.run()
        resultOrError match {
          case Left(error) => Left(parboiled.formatError(error))
          case z@Right(result) => Right(Some(firstPart + result))
        }
      case (false, _) =>
        val parboiled = new StompWireStompProtocolWithParboiled(next)
        val resultOrError = parboiled.ProperTermination.run()
        resultOrError match {
          case Left(error) => Left(parboiled.formatError(error))
          case _ => Right(None)
        }
    }
  }

  case class Header(header: String, value: String) {

  }

  case class Command2(value: String) extends AnyVal

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

  object Command {
    private val mayBody = Set("SEND", "MESSAGE", "ERROR")

    def mayHaveBody(value: String) = mayBody.contains(value)

    def mustNotHaveBody(value: String) = !mayBody.contains(value)
  }

}

class StompWireStompProtocolWithParboiled(val input: ParserInput) extends Parser {

  // SIZE LIMITS that DEPEND ON SERVER
  val maxHeadersPerFrame = 1 << 10
  val maxLengthOfHeaderLine = 1 << 10
  val maxBodySizeInBytes = 1 << 15


  val nullValue = "\u0000"

  def colon = rule {
    StompProtocol.COLON
  }

  def escape = rule {
    StompProtocol.ESCAPE
  }

  def comma = rule {
    StompProtocol.COMMA
  }

  def CommandAndHeaders = rule {
    command ~ EOL ~
      Headers ~
      EOL ~> ((c, h) => StompWireStompProtocolWithParboiled.CommandHeaders(c, h).toFrame)
  }

  def CommandHeadersAndCursor = rule {
    CommandAndHeaders ~ push(StompWireStompProtocolWithParboiled.Cursor(cursor))
  }

  def BodyWithNullTermination = rule {
    capture(noneOf(nullValue).*) ~ ProperTermination
  }

  def ProperTermination = rule {
    NULL ~ EOL.*
  }

  def NULL = rule {
    StompProtocol.NULL
  }

  def EOL = rule {
    optional(cr) ~ lf
  }

  def cr = rule {
    StompProtocol.CR
  }

  def lf = rule {
    StompProtocol.LF
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