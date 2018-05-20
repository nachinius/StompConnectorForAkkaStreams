package com.nachinius.akka.stream.stomp.protocol

import org.scalatest.{EitherValues, FunSuite, Matchers}
import org.parboiled2._
import Parser.DeliveryScheme.Either
import com.nachinius.akka.stream.stomp.protocol.parboiled.StompWireStompProtocolWithParboiled

class StompWireStompProtocolWithParboiledTest extends FunSuite with Matchers with EitherValues {



  val NULL: String = "\u0000"
  val goodFrames = Map(
    "justConnect" ->
      s"""CONNECT
        |
        |$NULL
      """.stripMargin,

    "message with headers" ->
      s"""MESSAGE
         |foo:World
         |bar:Hello
         |
         |$NULL
      """.stripMargin,

    "message with many EOL after ending" ->
      s"""MESSAGE
         |
         |$NULL
         |
         |
         |
       """.stripMargin,

    "message with body and no content-length" ->
      s"""MESSAGE
         |foo:World
         |
         |12345$NULL
       """.stripMargin,
    "message with body larger than content-length" ->
      s"""MESSAGE
         |foo:World
         |content-length:4
         |
         |12345$NULL
       """.stripMargin,
    "message with body with many NULL" ->
      s"""MESSAGE
         |foo:world
         |content-length:10
         |
         |12${NULL}456${NULL}89abc$NULL
       """.stripMargin

  )

  val badFrames = Map(
    "missing end of line command" ->
      s"""CONNECT
        |$NULL
      """.stripMargin,

    "missing null" ->
      s"""CONNECT
         |
       """.stripMargin,

  )


  def parse(msg: String) = {
    StompWireStompProtocolWithParboiled.parse(msg)
  }
  def report(name: String, input: String, expectation: Boolean = true) = {
//    println(s"for `$name` which has input\n----`$input`")
    val r = parse(input)
    if(expectation) {
      assert(r.isRight, if(r.isLeft) r.left.value else s"$name")
    } else {
      assert(r.isLeft, if(r.isRight) r.right.value else s"$name")
    }
//    println("\n____\n")
  }
  test("good messages should be parsed with no errors") {
    report("justConnect", goodFrames("justConnect"))
    goodFrames.foreach {
      case (name: String, input: String) => report(name,input, true)
    }
  }
  test("bad messages should fail") {
    badFrames.foreach {
      case (name: String, input: String) => report(name,input, false)
    }
  }
  test("correct header") {
    parse(goodFrames("justConnect")).right.value.command shouldBe "CONNECT"
    parse(goodFrames("message with headers")).right.value.command shouldBe "MESSAGE"
  }
  test("body should be the correct one") {
    parse(goodFrames("message with body and no content-length")).right.value.body.get shouldBe "12345"
  }
  test("a message with a body specified content-length must read at least the content-length indicated even when there are nulls in the indicated length") {
    val res = parse(goodFrames("message with body with many NULL"))
    val body = res.right.value.body.get
    body shouldBe s"12${NULL}456${NULL}89abc"
  }

}
