/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.nachinius.akka.stream.stomp.client

import akka.Done
import akka.stream._
import akka.stream.stage._
import io.vertx.ext.stomp.{Frame, StompClientConnection}

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

/**
 * Connects to a STOMP server upon materialization and receives incoming messages from the server to the stream.
 * Each materialized sink will create one connection to the server.
 */
final class SourceStage(settings: ConnectorSettings)
    extends GraphStageWithMaterializedValue[SourceShape[SendingFrame], Future[Done]] {
  stage =>

  val out = Outlet[SendingFrame]("StompClientSource.out")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val thePromise = Promise[Done]()
    val graphStageLogic = new GraphStageLogic(shape) with ConnectorLogic {

      override val settings: ConnectorSettings = stage.settings

      override val promise: Promise[Done] = thePromise
      override val fullFillOnConnection: Boolean = true

      private val headers: mutable.Map[String, String] =
        if (settings.withAck) mutable.Map(Frame.ACK -> "client-individual") else mutable.Map()

      var pending: Option[Frame] = None
      override def whenConnected(): Unit = {
        settings.destination match {
          case Some(dest) =>
            val receiveSubscriptionMessage = getAsyncCallback[Frame] { frame =>
              {
                pending = Some(frame)
                handleDelivery(frame)
              }

            }

            connection.subscribe(
              dest,
              headers.asJava, { frame: Frame =>
                receiveSubscriptionMessage.invoke(frame)
              }, { frame: Frame =>
                acknowledge(frame)
              }
            )
          case None =>
            val ex = new IllegalArgumentException("destination is required for StompClientSource")
            promise.tryFailure(ex)
            failStage(ex)
        }
      }

      override def receiveHandler(connection: StompClientConnection): Unit =
        connection.receivedFrameHandler({ frame: Frame =>
          {
            if (!settings.destination.contains(frame.getDestination)) {
              acknowledge(frame)
            }
          }
        })

      def handleDelivery(frame: Frame): Unit =
        if (isAvailable(out)) {
          pushMessage(frame)
        }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = pending.foreach(pushMessage)

          override def onDownstreamFinish(): Unit =
            completeStage()
        }
      )

      def pushMessage(frame: Frame): Unit = {
        push(out, SendingFrame.from(frame))
        pending = None
        acknowledge(frame)
      }

      override def postStop(): Unit = {
        promise.trySuccess(Done)
        super.postStop()
      }

      override def onFailure(ex: Throwable): Unit = {
        promise.trySuccess(Done)
        ()
      }

    }
    (graphStageLogic, thePromise.future)
  }

  override def shape: SourceShape[SendingFrame] = SourceShape.of(out)

  override def toString: String = "StompClientSource"

  override protected def initialAttributes: Attributes = SourceStage.defaultAttributes

}

object SourceStage {
  private val defaultAttributes =
    Attributes.name("StompClientSource").and(ActorAttributes.dispatcher("akka.stream.default-blocking-io-dispatcher"))
}
