[![Build Status](https://travis-ci.org/nachinius/StompConnectorForAkkaStreams.svg?branch=master)](https://travis-ci.org/nachinius/StompConnectorForAkkaStreams)
[![License](http://img.shields.io/:license-Apache%202-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

# STOMP Protocol Connector

The Stomp Protocol connector provides Akka Streams sources and sinks to connect to STOMP servers.

## Reported issues

[Tagged issues at Github](https://github.com/nachinius/StompConnectorForAkkaStreams/issues)


## Usage

### Connecting to a STOMP server

All the STOMP connectors are configured using a (com.nachinius.akka.stream.stomp.ConnectionProvider).

There are some types of (com.nachinius.akka.stream.stomp.ConnectionProvider):

* LocalConnectionProvider which connects to the default localhost. It creates a new connection for each stage.
* DetailsConnectionProvider which supports more fine-grained configuration. It creates a new connection for each stage.

### Sinking messages into a STOMP server

Create the ConnectorSettings

     val host = "localhost"
               val port = 61613
               val topic = "AnyTopic"
               val settings =
     ConnectorSettings(connectionProvider = DetailsConnectionProvider(host, port), destination = Some(topic)))
                                                            
StompClientSink is a collection of factory methods that facilitates creation of sinks. 

Create a sink, that accepts and forwards SendingFrame to the STOMP server.

      val sinkToStomp: Sink[SendingFrame, Future[Done]] = StompClientSink(settings)

Last step is to materialize and run the sink we created.

      val input = Vector("one", "two")
      val source = Source(input).map(SendingFrame.from)
      val sinkDone = source.runWith(sinkToStomp)

### Receiving messages from STOMP server using a StompClientSource

Create the [ConnectorSettings] that specifies the STOMP server to connect, and the STOMP `destination` that you want receive messages from

      val host = "localhost"
      val port = 61667
      val destination = "/topic/topic2"
      val settings = ConnectorSettings(DetailsConnectionProvider(host, port, None), Some(destination))ector-settings }

Create a source, that generates [SendingFrame]

      val source: Source[SendingFrame, Future[Done]] = StompClientSource(settings)

Last step is to materialize and run the source we created.

      val sink = Sink.head[SendingFrame]
      val (futConnected: Future[Done], futHead: Future[SendingFrame]) = source.toMat(sink)(Keep.both).run()


### Running an example code

The code in this guide is part of runnable tests of this project. You are welcome to edit the code and run it in sbt.

Test code does not require an STOMP server running in the background, since it creates one per test using Vertx Stomp library. 

Scala
:   ```
    sbt
    > test
    ```






