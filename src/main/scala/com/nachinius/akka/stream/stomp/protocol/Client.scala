package com.nachinius.akka.stream.stomp.protocol

import java.net.InetSocketAddress

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Tcp}
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import org.parboiled2.ParserInput
import org.parboiled2._

object A {

//  def connection = Tcp() outgoingConnection _ _

  //  def step = Flow[ByteString].via(Framing.delimiter(StompWireProtocol.FrameEnd)

  case class Header(header: String, value: String)
  case class Command(command: String)
  case class CommandHeaders(command: Command, headers: Seq[Header]) {
    def toFrame: Frame = {
      Frame(
        command,
        headers.groupBy(_.header).mapValues(_.head.value))
    }
  }
  case class Frame(command: Command, headers: Map[String,String], body: Option[ByteString] = None)

}

class StompWireProtocol(val input: ParserInput) extends Parser {


  def NULL = rule { "\u0000" }
  def cr = rule { "\r" }
  def lf = rule { "\n" }
  def colon = rule { ':' }
  def escape = rule { '\\' }
  def comma = rule { ',' }

  def EOL = rule { optional(cr) ~ lf }

  def BeginningOfAFrame = rule { command ~ EOL ~
                                      Headers ~
                                      EOL ~> ((c,h) => A.CommandHeaders(c,h).toFrame) }

  def Frame = rule { BeginningOfAFrame }

  def command = rule { (clientCommand | serverCommand) ~> A.Command }
  def clientCommand = rule { capture("SEND" | "SUBSCRIBE" | "UNSUBSCRIBE" | "BEGIN" | "COMMIT" | "ABORT" | "ACK" | "NACK" | "DISCONNECT" | "CONNECT" | "STOMP") }
  def serverCommand = rule {capture("CONNECTED" | "MESSAGE" | "RECEIPT" | "ERROR") }

  def Headers = rule { Header.* }
  def Header = rule { HeaderName ~ colon ~ HeaderValue ~ EOL ~> A.Header }
  def NormalString = rule { capture(noneOf(":\r\n").+) }
  def HeaderName = rule { NormalString }
  def HeaderValue = rule { NormalString }

}