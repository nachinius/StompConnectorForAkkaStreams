package com.nachinius.akka.stream.stomp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, Keep, Source, Tcp, _}
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.util.ByteString
import com.nachinius.akka.stream.stomp.protocol.StompCommand._
import com.nachinius.akka.stream.stomp.protocol.{Frame, StompCommand}
import com.nachinius.akka.stream.stomp.protocol.parboiled.ParboiledImpl

import scala.concurrent.Future

object StompClientFlow {
  val DEBUG = true

  case class Settings(host: String, port: Int, tcp: Tcp)

  def client(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    //    StompClientConnection
    framedFlow
  }

  def client(host: String, port: Int, tcp: Tcp): Flow[Frame, Frame, Future[OutgoingConnection]] = client(Settings(host, port, tcp))

  def stompConnectedFlow(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    Flow.fromGraph(GraphDSL.create(framedFlow(settings)) { implicit b =>
      tcpFlow =>
        import GraphDSL.Implicits._

        val connecting = b.add(Source.single(connectFrame(settings.host)))

        val broadcast = b.add(Broadcast[Frame](2, false))
        //      val mergePreferred = b.add(MergePreferred[Frame](1,false))
        val concat = b.add(Concat[Frame](2))

        connecting ~> concat ~> tcpFlow ~> broadcast

        val processConnectionParameters = Sink.ignore
        // handle "CONNECTED"
        broadcast ~> Flow[Frame].dropWhile(!_.isConnected).take(1) ~> processConnectionParameters

        // handle all the rest
        val out = broadcast ~> Flow[Frame].collect({
          case error@Frame(ERROR, _, _) => throw new StompProtocolError("Error frame received\n" + error.toString)
          case msg@Frame(MESSAGE, _, _) => msg
          case unknown@Frame(StompCommand.OTHER(str), _, _) => throw new StompProtocolError(s"Unknown stomp command received from server 'str'\n" + unknown.toString)
          //        case _ => //ignored
        })

        // expose ports
        FlowShape(concat.in(1), out.outlet)
    })

  }


  /**
    * Pure Flow connection to a TCP that as input and outputs has Frames
    *
    * @param settings
    * @return
    */
  private[stomp] def framedFlow(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    val tcpFlow: Flow[ByteString, ByteString, Future[OutgoingConnection]] = settings.tcp.outgoingConnection(settings.host, settings.port)

    toByteString.
      via(debugFlow[ByteString]("\nSending-->\n" + _.utf8String + "\n<----")).
      viaMat(tcpFlow)(Keep.right).
      via(debugFlow("\nReceiving-->\n" + _.utf8String + "\n<----")).
      via(fromByteString)
  }

  private def debugFlow[A](msg: A => String): Flow[A, A, NotUsed] = Flow[A].map[A]({ elem => {
    if (DEBUG) println("------>In debug flow:\n" + msg(elem) + "\n<<<<<<<<<<<<<")
  };
    elem
  })

  /**
    * Encode a ByteString to a Frame
    *
    * @return
    */
  private def fromByteString: Flow[ByteString, Frame, NotUsed] = Flow.fromFunction((value: ByteString) => ParboiledImpl.decode(value.utf8String) match {
    case Right(frame) => frame
    case Left(msg) => throw new StompProtocolError("while decoding " + msg)
  })

  /**
    * Decodes a Frame to a ByteString
    *
    * @return
    */
  private def toByteString: Flow[Frame, ByteString, NotUsed] = Flow.fromFunction((value: Frame) => ParboiledImpl.encode(value) match {
    case Right(str) => ByteString(str)
    case Left(msg) => throw new StompProtocolError("while encoding " + msg)
  })

  /**
    * A Simple Frame message of type CONNECT
    *
    * @return
    */
  def connectFrame(host: String): Frame = Frame(CONNECT, Map(
    Frame.Header.acceptVersion -> Seq("1.2"),
    Frame.Header.host -> Seq(host),
    Frame.Header.heartBeat -> Seq("0,0")
  ))
}

class StompProtocolError(msg: String) extends Exception {
  override def toString: String = super.toString + msg
}
