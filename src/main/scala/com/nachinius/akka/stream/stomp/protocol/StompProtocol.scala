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

case class Frame(command: StompCommand, headers: Map[String, Seq[String]] = Map(), body: Option[String] = None) {
  self =>
  def withDestination(dest: String) = {
    val nextHeaders = headers + (Frame.Header.destination -> Seq(dest))
    self.copy(headers = nextHeaders)
  }

  def isClientFrame = command.isInstanceOf[ClientCommand]

  def isServerFrame = command.isInstanceOf[ServerCommand]

  def isMessage = command == StompCommand.MESSAGE

  def isError = command == StompCommand.ERROR

  def isConnected = command == StompCommand.CONNECTED
}

object Frame {

  object Header {
    val acceptVersion = "accept-version"
    val host = "host"
    val login = "login"
    val passcode = "passcode"
    val heartBeat = "heart-beat"
    val version = "version"
    val destination = "destination"
  }

}
