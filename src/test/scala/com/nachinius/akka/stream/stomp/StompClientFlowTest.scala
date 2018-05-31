package com.nachinius.akka.stream.stomp

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
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
  val settings = Settings(host, port, Tcp())
  val connectFrame = Frame(StompCommand.CONNECT, Map(Frame.Header.acceptVersion -> Seq("1.2")), None)
  val sendFrame = Frame(StompCommand.SEND, Map("hola" -> Seq("Houston")), Some("some part of the body")).withDestination("any")

  "framedFlow" must {
    "send CONNECT frame and receive a CONNECTED frame" in {
      val server = getStompServer(None, port)

      val flowUnderTest = StompClientFlow.framedFlow(settings)


      val (pub, fut, sub) = probeFlow(flowUnderTest)

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

  "stompConnectedFlow" must {
    "be able to send MESSAGES as first stomp messages" in {
      val server = getStompServer(None, port)

      val flowUnderTest = StompClientFlow.stompConnectedFlow(settings)

      val (pub, fut, sub) = probeFlow(flowUnderTest)

      sub request 1
      pub sendNext sendFrame
      sub expectNoMessage (patience) // the server configured does not reply on messages unless there is an error
      pub sendComplete()
      sub expectComplete()

      Await.ready(fut, patience)

      closeAwaitStompServer(server)
    }
    "cancel when it receives an ERROR frame from the server" in {
      val server = stompServerWithTopicAndQueue(port)

      val flowUnderTest = StompClientFlow.stompConnectedFlow(settings)

      val (pub, fut, sub) = probeFlow(flowUnderTest)

      sub request 1
      pub sendNext sendFrame.replaceHeader("destination", "\n\n\n")
      sub expectNoMessage (patience) // the server configured does not reply on messages unless there is an error
      pub sendComplete()
      sub expectComplete()

      Await.ready(fut, patience)

      closeAwaitStompServer(server)
    }

  }

  private def probeFlow(flowUnderTest: Flow[Frame, Frame, Future[Tcp.OutgoingConnection]]): (TestPublisher.Probe[Frame], Future[Tcp.OutgoingConnection], TestSubscriber.Probe[Frame]) = {
    val ((pub, fut), sub) = TestSource.probe[Frame].viaMat(flowUnderTest)(Keep.both).toMat(TestSink.probe[Frame])(Keep.both).run()
    (pub, fut, sub)
  }
}
