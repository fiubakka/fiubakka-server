package server.domain.structs

import akka.actor.typed.ActorRef
import akka.serialization.jackson.CborSerializable
import server.protocol.client.PlayerHandler

final case class PlayerPosition(x: Float, y: Float)

final case class DurablePlayerState(
    handler: ActorRef[PlayerHandler.Command],
    position: PlayerPosition
) extends CborSerializable

final case class TransientPlayerState(
    knownGameEntities: Map[String, GameEntity]
)

final case class PlayerState(
    dState: DurablePlayerState,
    tState: TransientPlayerState
)
