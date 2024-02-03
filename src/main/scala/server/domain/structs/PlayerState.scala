package server.domain.structs

import akka.actor.typed.ActorRef
import akka.serialization.jackson.CborSerializable
import server.domain.entities.Player
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity

import java.time.LocalDateTime

final case class DurablePlayerState(
    position: Position,
    equipment: Equipment
) extends CborSerializable

final case class TransientPlayerState(
    handler: ActorRef[Player.ReplyCommand],
    lastHeartbeatTime: LocalDateTime,
    velocity: Velocity
)

final case class PlayerState(
    dState: DurablePlayerState,
    tState: TransientPlayerState
)
