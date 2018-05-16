package com.nachinius.akka.stream.stomp.protocol

import org.scalatest.FunSuite
import org.parboiled2._
import Parser.DeliveryScheme.Either

class StompWireProtocolTest extends FunSuite {

  val CONNECT = "CONNECT\n\n"
  val SEND = "SEND\nhola:mundo\nping:pong pong\n\n"

  test("first example") {

    val parser = new StompWireProtocol(SEND)
    val result = parser.BeginningOfAFrame.run()

    result match {
      case Right(x) =>
        println(x)
        assert(true)
      case Left(y: ParseError) =>
        println(parser.formatError(y))
        assert(false)
    }

  }


}
