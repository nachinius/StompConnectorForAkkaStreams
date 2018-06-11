package com.nachinius.akka.stream.stomp

import akka.stream._
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{Flow, Keep, Source, Tcp, _}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.nachinius.akka.stream.stomp.protocol.Frame
import com.nachinius.akka.stream.stomp.protocol.StompCommand._
import com.nachinius.akka.stream.stomp.protocol.parboiled.ParboiledImpl

import scala.concurrent.Future

object StompClientFlow {
  val DEBUG = true
  val anyFrame = Frame(OTHER("never to be deliver to server"))

  def client(host: String, port: Int, tcp: Tcp): Flow[Frame, Frame, Future[OutgoingConnection]] = client(Settings(host, port, tcp))

  def client(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    //    StompClientConnection
    stompConnectedFlow
  }

  /**
    * Flow to a Stomp Server, where the 'connection' has been established
    *
    * @param settings
    * @return
    */
  def stompConnectedFlow(implicit settings: Settings): Flow[Frame, Frame, Future[OutgoingConnection]] = {
    connectToStompStep.atop(ByteStringFrameCodec).joinMat(tcpFlow)(Keep.right)
  }

  /**
    * Stage that handles the CONNECTING part of the STOMP protocol
    *
    * @param settings
    * @return
    */
  def connectToStompStep(implicit settings: Settings): BidiFlow[Frame, Frame, Frame, Frame, NotUsed] = {
    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val connecting: SourceShape[Frame] = b.add(Source.single(connectFrame(settings.host)))
      val concat: UniformFanInShape[Frame, Frame] = b.add(Concat[Frame](2))

      val connected = 0
      val anyOther = 1
      val partition = b.add(Partition[Frame](2, { frame =>
        if (frame.isConnected) connected else anyOther
      }))

      val throwProblems: Flow[Frame, Frame, NotUsed] = Flow[Frame].map { frame =>
        if (frame.isError)
          throw new StompProtocolError("Error frame received\n" + frame.toString)
        else
          frame
      }

      /**
        * A bidirectional flow of elements that consequently has two inputs and two
        * outputs, arranged like this:
        *
        * {{{
        *        +------------------------------------------------+
        *        | connecting ~> concat                           |
        *  In1 ~>|            ~> concat ~>                        |~> Out1
        *        |                                                |
        *        |    ignore <~ connected  -  partition                    |<~ In2
        * Out2 <~|           <~ throwProblems <~ anyOther - partition      |
        *        +------------------------------------------------+
        * }}}
        */

      connecting ~> concat.in(0)
      partition.out(connected) ~> Sink.ignore // do something with the connected message
    val out = partition.out(anyOther) ~> throwProblems

      BidiShape.of(concat.in(1), concat.out, partition.in, out.outlet)
    })
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

  private def tcpFlow(implicit settings: Settings): Flow[ByteString, ByteString, Future[OutgoingConnection]] = {
    val bufferFlow = settings.buffer.fold(Flow[ByteString].map(identity))(size => Flow[ByteString].buffer(size, settings.overflowStrategy))
    BidiFlow.fromFlows(bufferFlow,bufferFlow).joinMat(
      settings.tcp.outgoingConnection(settings.host, settings.port)
    )(Keep.right)
  }

  private def ByteStringFrameCodec = BidiFlow.fromFunctions(frameToByteString, byteStringToFrame)

  /**
    * Encode a ByteString to a Frame
    *
    * @return
    */
  private def byteStringToFrame(value: ByteString): Frame = {
    val utf8String = value.utf8String
    if (DEBUG) println("decoding\n" + utf8String)
    ParboiledImpl.decode(utf8String) match {
      case Right(frame) => frame
      case Left(msg) => throw new StompProtocolError("while decoding " + msg)
    }
  }

  /**
    * Decodes a Frame to a ByteString
    *
    * @return
    */
  private def frameToByteString(frame: Frame): ByteString = ParboiledImpl.encode(frame) match {
    case Right(str) => {
      if (DEBUG) {
        println("encoding\n" + str)
      } else {}
      ByteString(str)
    }
    case Left(msg) => throw new StompProtocolError("While encoding " + msg)
  }

  /**
    * Source that connects to a Stomp Server, and subscribes to the given topic, emitting all elements received (for that topic)
    *
    * @param topic
    * @param settings
    * @return
    */
  def subscribeTopic(topic: String)(implicit settings: Settings): Source[Frame, (Future[Done], Future[OutgoingConnection])] = {
    Source.maybe.viaMat(
      subscribeBidiFlow(topic).atop(connectToStompStep).atop(ByteStringFrameCodec).joinMat(tcpFlow)(Keep.both)
    )(Keep.right)
  }

  /**
    * Stage that handle subscribing to a STOMP server's topic (holds flows until subscription is confirmed).
    *
    * Materializes with a future that indicates when the subscription is acknowledge by the server, and thus is standing.
    */
  def subscribeBidiFlow(topic: String)(implicit settings: Settings): BidiFlow[Frame, Frame, Frame, Frame, Future[Done]] =
    BidiFlow.fromGraph(
      GraphDSL.create(Sink.head[Done]) {
        implicit b =>
          sink =>
            import GraphDSL.Implicits._

            def isWaitedFrame(receiptId: String)(frame: Frame): Boolean = {
              frame.isReceipt && frame.headers.get(Frame.Header.receiptId).flatMap(_.headOption).contains(receiptId)
            }

            def isFrameOfSubscription(subscriptionId: String)(frame: Frame): Boolean = {
              println("checking for subscription "+subscriptionId + " in \n" + frame.toString)
              true
//              frame.headers.get(Frame.Header.subscription).flatMap(_.headOption).contains(subscriptionId)
            }
            def print[A](extra: String = "") = Flow[A].map[A]({ f =>
              println("start...----[[["+extra);
              println(f);
              println("end.....----"+extra+"]]]")
              f
            })

            val receiptId = "subscribeMeId0"
            val subscriptionId = "0"

            val start = b.add(Source.single(subscribeFrame(topic, subscriptionId, Some(receiptId))))

            val concat = b.add(Concat[Frame](2))

            start ~> print[Frame]("using start") ~> concat.in(0)

            val out1 = concat ~> print[Frame]("out of concat")


            val bcast = b.add(Broadcast[Frame](2,false))
//            val onlyReceipt = b.add(Flow[Frame].filter(_.isReceipt))

            val in = b.add(print[Frame]("receiving"))
            in ~> bcast ~> print[Frame]("receipt?") ~> Flow[Frame].filter(_.isReceipt) ~> print[Frame]("Is a receipt!") ~> Flow[Frame].map(_ => Done) ~> print[Done]("Is connected done!") ~> sink
            val out2 = bcast ~> Flow[Frame].filterNot(_.isReceipt) ~> print[Frame]("not a receipt!")

            BidiShape(concat.in(1),out1.outlet, in.in, out2.outlet)

      })

  def subscribeFrame(topic: String, id: String = "0", receiptId: Option[String] = None): Frame = {
    val frame = Frame(SUBSCRIBE).
      addHeader("id", id).
      addHeader("destination", topic).
      addHeader("ack", "auto")
    receiptId.map(id => frame.addHeader("receipt", id)).getOrElse(frame)
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

  case class Settings(host: String, port: Int, tcp: Tcp, buffer: Option[Int] = Some(10), overflowStrategy: OverflowStrategy = OverflowStrategy.dropHead)

}

class StompProtocolError(msg: String) extends Exception {
  override def toString: String = super.toString + msg
}
