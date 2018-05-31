package com.nachinius.akka.stream.stomp.protocol

object StompCommand {
  def fromString(str: String): StompCommand = str match {
    case "SEND" => SEND
    case "SUBSCRIBE" => SUBSCRIBE
    case "UNSUBSCRIBE" => UNSUBSCRIBE
    case "BEGIN" => BEGIN
    case "COMMIT" => COMMIT
    case "ABORT" => ABORT
    case "ACK" => ACK
    case "NACK" => NACK
    case "DISCONNECT" => DISCONNECT
    case "CONNECT" => CONNECT
    case "STOMP" => STOMP
    case "CONNECTED" => CONNECTED
    case "MESSAGE" => MESSAGE
    case "RECEIPT" => RECEIPT
    case "ERROR" => ERROR
    case _ => OTHER(str)
  }

  // CLIENT =================
  case object SEND extends ClientCommand

  case object SUBSCRIBE extends ClientCommand

  case object UNSUBSCRIBE extends ClientCommand

  case object BEGIN extends ClientCommand

  case object COMMIT extends ClientCommand

  case object ABORT extends ClientCommand

  case object ACK extends ClientCommand

  case object NACK extends ClientCommand

  case object DISCONNECT extends ClientCommand

  case object CONNECT extends ClientCommand

  case object STOMP extends ClientCommand

  /**
    * Specification: "A client MAY send a frame not in this list, but for such a frame a STOMP 1.2 server MAY respond with an ERROR frame and then close the connection."
    */
  case class OTHER(value: String) extends ClientCommand {
    override val asString = value
  }


  // SERVER ================
  case object CONNECTED extends ServerCommand

  case object MESSAGE extends ServerCommand

  case object RECEIPT extends ServerCommand

  case object ERROR extends ServerCommand

  //==================================================


}

//==================================================
// COMMANDS
trait StompCommand {
  def asString: String = toString
}

trait ServerCommand extends StompCommand

trait ClientCommand extends StompCommand

