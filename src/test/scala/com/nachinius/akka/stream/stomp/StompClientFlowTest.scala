package com.nachinius.akka.stream.stomp

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source, Tcp}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import com.nachinius.akka.stream.stomp.StompClientFlow.Settings
import com.nachinius.akka.stream.stomp.client.StompClientSpec
import com.nachinius.akka.stream.stomp.client.Server._
import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncFreeSpec, Matchers}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

class StompClientFlowTest extends StompClientSpec {

  val port = 61600
  val host = "localhost"
  val connectFrame = Frame(StompCommand.CONNECT, Map(Frame.Header.acceptVersion -> Seq("1.2")), None)
  val sendFrame = Frame(StompCommand.SEND, Map("hola" -> Seq("Houston")), Some("some part of the body")).withDestination("any")

  "tcpConnectionFlow" must {
    "send CONNECT frame and receive a CONNECTED frame" in {
      val settings = Settings(host, port, Tcp())
      val flowUnderTest = StompClientFlow.framedFlow(settings)

      val server = getStompServer(None, port)

      val ((pub, fut), sub) =
        TestSource.probe[Frame].viaMat(flowUnderTest)(Keep.both).toMat(TestSink.probe[Frame])(Keep.both).run()

      sub.request(1)
      pub sendNext connectFrame
      sub.expectNext().command == StompCommand.CONNECTED
      pub sendNext sendFrame
      pub sendComplete()
      sub expectComplete()

      val patience = FiniteDuration(1, "seconds")
      Await.ready(fut, patience)

      closeAwaitStompServer(server)
    }
  }

  "stompClientConnection" must {
    "be able to send MESSAGES as first stomp messages" in {
      val settings = Settings(host, port, Tcp())
      val flowUnderTest = StompClientFlow.stompConnectedFlow(settings)

      val server = getStompServer(None, port)

      val ((pub, fut), sub) = TestSource.probe[Frame].viaMat(flowUnderTest)(Keep.both).toMat(TestSink.probe[Frame])(Keep.both).run()

      //      sub request 10
      //      pub sendNext sendFrame
      //      sub expectNoMessage (patience)
      sub request 1
      pub sendComplete()
      sub expectComplete()

      Await.ready(fut, patience)

      closeAwaitStompServer(server)
    }

  }
}
