package server.domain.structs

import akka.serialization.jackson.CborSerializable

final case class PlayerPosition(x: Float, y: Float)

final case class PlayerState(position: PlayerPosition) extends CborSerializable
