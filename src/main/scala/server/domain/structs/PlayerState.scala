package server.domain.structs

import akka.serialization.jackson.CborSerializable

final case class PlayerPosition(x: Int, y: Int)

final case class PlayerState(position: PlayerPosition) extends CborSerializable
