package com.nachinius.akka.stream.stomp.protocol

trait StompProtocol {
  def decode(input: String): Either[String, Frame]

  def encode(frame: Frame): Either[String, String]
}

object StompProtocol {
  val NULL = "\u0000"
  val CR = "\r"
  val LF: String = "\n"
  val COLON = ':'
  val ESCAPE: Char = '\\'
  val COMMA = ','
}

case class Frame(command: StompCommand, headers: Map[String, Seq[String]], body: Option[String] = None)
