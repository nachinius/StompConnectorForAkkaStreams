package com.nachinius.akka.stream.stomp.protocol

import akka.util.ByteString
import io.vertx.core.parsetools.RecordParser

sealed trait ParsingStep
case object COMMAND extends ParsingStep
case object HEADERS extends ParsingStep
case object BODY extends ParsingStep

class StompParser {


  var state: ParsingStep = COMMAND
  var headers = Map[String,String]()

  def handleStep(buffer: ByteString): Unit = {
    state match {
      case COMMAND => parseCommand(buffer)
      case _ => ()
    }
  }

  def parseCommand(buffer: ByteString) = {
  }
}


