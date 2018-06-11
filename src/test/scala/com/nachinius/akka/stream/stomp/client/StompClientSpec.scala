
package com.nachinius.akka.stream.stomp.client

import akka.actor.ActorSystem
import akka.dispatch.ExecutionContexts
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.vertx.core.{Vertx, VertxOptions}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpec}

import scala.concurrent.duration._

trait StompClientSpec extends WordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {

  val testConf: Config = ConfigFactory.parseString("""
  akka {
    loggers = ["akka.testkit.TestEventListener"]
    loglevel = "DEBUG"
    stdout-loglevel = "DEBUG"
    actor {
      default-dispatcher {
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 8
          parallelism-factor = 2.0
          parallelism-max = 8
        }
      }
    }
  }""")

  implicit val system: ActorSystem = ActorSystem(this.getClass.getSimpleName, testConf)
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

  val patience = 2.seconds

  override implicit val patienceConfig = PatienceConfig(patience)
  implicit val executionContext = ExecutionContexts.global()

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
