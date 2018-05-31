package com.nachinius.akka.stream.stomp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{BidiShape, FlowShape}
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
    stompConnectedFlow
  }

  def client(host: String, port: Int, tcp: Tcp): Flow[Frame, Frame, Future[OutgoingConnection]] = client(Settings(host, port, tcp))

  /**
    * Flow subscribed to a specific stomp server's topic
    */


  /**
    * Flow to a Stomp Server, where the 'connection' has been established
    *
    * @param settings
    * @return
    */
  def stompConnectedFlow(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    connectToStompStep.atop(ByteStringFrameCodec).joinMat(tcpFlow)(Keep.right)
  }

  def connectToStompStep(implicit settings: Settings): BidiFlow[Frame, Frame, Frame, Frame, NotUsed] = {
    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val connecting = b.add(Source.single(connectFrame(settings.host)))
      val concat = b.add(Concat[Frame](2))

      connecting ~> concat

      val broadcast = b.add(Broadcast[Frame](2, false))

      broadcast ~> Flow[Frame].dropWhile(f => !f.isConnected).take(1) ~> Sink.ignore

      val out = broadcast ~> Flow[Frame].collect({
        case msg@Frame(MESSAGE, _, _) => msg
        case error@Frame(ERROR, _, _) => throw new StompProtocolError("Error frame received\n" + error.toString)
        case unknown@Frame(StompCommand.OTHER(str), _, _) => throw new StompProtocolError(s"Unknown stomp command received from server 'str'\n" + unknown.toString)
      })

      BidiShape.of(concat.in(1), concat.out, broadcast.in, out.outlet)
    })
  }

  /**
    * Pure Flow connection to a Tcp connection that as input and outputs as Stomp Frames
    *
    * @param settings
    * @return
    */
  private[stomp] def framedFlow(implicit settings: Settings) = {
    ByteStringFrameCodec.joinMat(tcpFlow)(Keep.right)
  }

  private def tcpFlow(implicit settings: Settings): Flow[ByteString, ByteString, Future[OutgoingConnection]] = {
    settings.tcp.outgoingConnection(settings.host, settings.port)
  }

  private def ByteStringFrameCodec = BidiFlow.fromFunctions(frameToByteString, byteStringToFrame)

  /**
    * Encode a ByteString to a Frame
    *
    * @return
    */
  private def byteStringToFrame(value: ByteString): Frame = ParboiledImpl.decode(value.utf8String) match {
    case Right(frame) => frame
    case Left(msg) => throw new StompProtocolError("while decoding " + msg)
  }

  /**
    * Decodes a Frame to a ByteString
    *
    * @return
    */
  private def frameToByteString(frame: Frame): ByteString = ParboiledImpl.encode(frame) match {
    case Right(str) => ByteString(str)
    case Left(msg) => throw new StompProtocolError("While encoding " + msg)
  }

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
