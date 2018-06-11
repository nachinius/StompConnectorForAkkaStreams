package com.nachinius.akka.stream.stomp

import akka.{Done, NotUsed}
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import com.nachinius.akka.stream.stomp.StompClientFlow.Settings
import com.nachinius.akka.stream.stomp.client.{ConnectorSettings, DetailsConnectionProvider, SendingFrame, StompClientSpec}
import com.nachinius.akka.stream.stomp.client.Server._
import com.nachinius.akka.stream.stomp.scaladsl.StompClientSource
import com.nachinius.akka.stream.stomp.protocol.parboiled.ParboiledImpl
import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand}
import io.vertx.ext.stomp.StompServer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncFreeSpec, Matchers}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

class StompClientFlowTest extends StompClientSpec {

  val port = 61600
  val host = "localhost"
  implicit val settings = Settings(host, port, Tcp(),Some(5))
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
    "emit all messages sent to the stomp topic that is registered to" in {
      // this server handle topics, and just published whatever receiver under such topic
      val server: StompServer = stompServerWithTopicAndQueue(port)

      val topic = "mytopic"
      val badTopic = "bad"

      // a client and probe to submit message to the Stomp server under topic `topic`
      val publisher: (TestPublisher.Probe[Frame], Future[Tcp.OutgoingConnection]) = TestSource.probe[Frame].map({
        frame => frame.replaceHeader(Frame.Header.destination,topic)
      }).viaMat(StompClientFlow.stompConnectedFlow(settings))(Keep.both).to(Sink.ignore).run()

      // a client and probe to submit message to the Stomp server under a different topic
      val otherPublisher: (TestPublisher.Probe[Frame], Future[Tcp.OutgoingConnection]) = TestSource.probe[Frame].map({
        frame => frame.replaceHeader(Frame.Header.destination, badTopic)
      }).viaMat(StompClientFlow.stompConnectedFlow(settings))(Keep.both).to(Sink.ignore).run()

      // A test client using another technology
      val vertxSubscriber: (Future[Done], TestSubscriber.Probe[SendingFrame]) =
        StompClientSource(
          ConnectorSettings(
            DetailsConnectionProvider(host, port, None),
            Some(topic)
          )).
          toMat(TestSink.probe)(Keep.both).run()

      // code under test
      val underTest: ((Future[Done], Future[Tcp.OutgoingConnection]), TestSubscriber.Probe[Frame]) =
        StompClientFlow.subscribeTopic(topic).
          toMat(TestSink.probe[Frame])(Keep.both).
          run()


      // wait for connections
      underTest._1._2.futureValue
      vertxSubscriber._1.futureValue
      publisher._2.futureValue
      otherPublisher._2.futureValue

      // with some demand
      underTest._2.request(1)
      Thread.sleep(1000)
      // wait for subscription acknowledgemente
      Await.ready(underTest._1._1,patience)
      underTest._1._1.futureValue shouldBe Done

      (1 to 5).foreach { x =>
        publisher._1.sendNext(sendFrame.withBody(x.toString))
        otherPublisher._1.sendNext(sendFrame.withBody((x+100).toString))
      }
      underTest._2.expectNext().body shouldBe Some("1")
      underTest._2.expectNoMessage(FiniteDuration(500,"milliseconds"))
      underTest._2.request(2)
      underTest._2.expectNextN(2).map(_.body).flatten should contain only ("2","3")
      println("vertx received " + vertxSubscriber._2.requestNext().toVertexFrame)
      underTest._2.request(2)
      underTest._2.expectNextN(2).map(_.body).flatten should contain only ("4","5")
      underTest._2.cancel()
      vertxSubscriber._2.cancel()
      publisher._1.sendComplete()
      otherPublisher._1.sendComplete()

      closeAwaitStompServer(server)
    }
  }

  private def probeFlow(flowUnderTest: Flow[Frame, Frame, Future[Tcp.OutgoingConnection]]): (TestPublisher.Probe[Frame], Future[Tcp.OutgoingConnection], TestSubscriber.Probe[Frame]) = {
    val ((pub, fut), sub) = TestSource.probe[Frame].viaMat(flowUnderTest)(Keep.both).toMat(TestSink.probe[Frame])(Keep.both).run()
    (pub, fut, sub)
  }
}
