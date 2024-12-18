package server.domain.structs

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.serialization.jackson.CborSerializable
import server.domain.entities.player.Player
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.infra.PlayerPersistor
import server.protocol.event.GameEventConsumer
import server.protocol.event.GameEventProducer
import server.utils.ActorMetrics

import java.time.LocalDateTime

final case class DurablePlayerState(
    playerName: String, // Comes in handy when we need to access the player's name
    position: Position,
    equipment: Equipment,
    mapId: Int
) extends CborSerializable

final case class TransientPlayerState(
    handler: ActorRef[Player.ReplyCommand],
    eventProducer: ActorRef[GameEventProducer.Command],
    eventConsumer: ActorRef[GameEventConsumer.Command],
    persistor: EntityRef[PlayerPersistor.Command],
    lastHeartbeatTime: LocalDateTime,
    velocity: Velocity,
    metrics: ActorMetrics
)

final case class PlayerState(
    dState: DurablePlayerState,
    tState: TransientPlayerState
)
