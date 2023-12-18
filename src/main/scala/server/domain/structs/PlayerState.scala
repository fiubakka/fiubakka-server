package server.domain.structs

import akka.actor.typed.ActorRef
import akka.serialization.jackson.CborSerializable
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.protocol.client.PlayerHandler

import java.time.LocalDateTime

final case class DurablePlayerState(
    handler: ActorRef[PlayerHandler.Command],
    position: Position
) extends CborSerializable

final case class TransientPlayerState(
    lastHeartbeatTime: LocalDateTime,
    velocity: Velocity
)

final case class PlayerState(
    dState: DurablePlayerState,
    tState: TransientPlayerState
)
