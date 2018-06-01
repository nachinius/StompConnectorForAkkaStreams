package com.nachinius.akka.stream.stomp

import akka.{Done, NotUsed}
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import com.nachinius.akka.stream.stomp.StompClientFlow.Settings
import com.nachinius.akka.stream.stomp.client.StompClientSpec
import com.nachinius.akka.stream.stomp.client.Server._
import com.nachinius.akka.stream.stomp.protocol.parboiled.ParboiledImpl
import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncFreeSpec, Matchers}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

class StompClientFlowTest extends StompClientSpec {

  val port = 61600
  val host = "localhost"
  implicit val settings = Settings(host, port, Tcp())
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

  "A stomp Source" must {
    "emit all messages sent to a stomp topic" in {
      // this server handle topics, and just published whatever receiver under such topic
      val server = stompServerWithTopicAndQueue(port)

      val topic = "mytopic"

      // a client and probe to submit message to the Stomp server under topic `topic`
      val (pub, futpub) = TestSource.probe[Frame].map({
        frame => frame.addHeader(Frame.Header.destination,topic)
      }).viaMat(StompClientFlow.stompConnectedFlow(settings))(Keep.both).to(Sink.ignore).run()
      // a client and probe to submit message to the Stomp server under topic `topic`

      val (pubToAnotherTopic, futpub2) = TestSource.probe[Frame].map({
        frame => frame.addHeader(Frame.Header.destination,"anyothertopic" + topic)
      }).viaMat(StompClientFlow.stompConnectedFlow(settings))(Keep.both).to(Sink.ignore).run()

      // code under test
      val source = StompClientFlow.subscribeTopic(topic)
      val ((futsub: Future[Done],futconn), sub) = source.toMat(TestSink.probe[Frame])(Keep.both).run()

      Await.ready(futconn,patience)
      Await.ready(futsub,patience)

      import StompCommand.SEND
      pub sendNext Frame(SEND,Map(),Some("hi"))
      pubToAnotherTopic sendNext Frame(SEND,Map(),Some("nohi"))

      val msg1 = sub requestNext()
      msg1.body shouldBe Some("hi2")

      sub expectNoMessage(patience)

      pub sendNext Frame(SEND,Map(),Some("is there anybody there?"))
      (sub expectNext() body) shouldBe Some("is there anybody there?")


      pub sendComplete()
      sub cancel()


      closeAwaitStompServer(server)
    }
  }

  private def probeFlow(flowUnderTest: Flow[Frame, Frame, Future[Tcp.OutgoingConnection]]): (TestPublisher.Probe[Frame], Future[Tcp.OutgoingConnection], TestSubscriber.Probe[Frame]) = {
    val ((pub, fut), sub) = TestSource.probe[Frame].viaMat(flowUnderTest)(Keep.both).toMat(TestSink.probe[Frame])(Keep.both).run()
    (pub, fut, sub)
  }
}
