package server.domain.structs

import akka.serialization.jackson.CborSerializable

final case class PlayerPosition(x: Float, y: Float)

final case class DurablePlayerState(position: PlayerPosition)
    extends CborSerializable

final case class TransientPlayerState(
    knownGameEntities: Map[String, GameEntity]
)

final case class PlayerState(
    dState: DurablePlayerState,
    tState: TransientPlayerState
)
