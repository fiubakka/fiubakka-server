package server.protocol.event

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.util.ByteString
import protobuf.event.chat.message.PBPlayerMessage
import protobuf.event.metadata.PBEventMetadata
import protobuf.event.state.game_entity_disconnect.PBGameEntityDisconnect
import protobuf.event.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import server.domain.entities.player.Player
import server.domain.structs.GameEntityState
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.protocol.event.kafka.KafkaConsumer
import server.protocol.flows.InMessageFlow

object GameEventConsumer {
  sealed trait Command extends CborSerializable
  final case class EventReceived(msg: GeneratedMessage) extends Command
  final case class Start() extends Command

  def apply(
      player: ActorRef[Player.Command],
      partition: Int
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)
      val playerId =
        player.path.name // The Player Entity Id is its Actor's name

      KafkaConsumer(partition)
        .named("GameEventConsumer")
        .buffer(1024, OverflowStrategy.dropHead)
        .filter(record => {
          (record.key == null || record.key != playerId)
        }) // Ignore messages from myself
        .map { record =>
          ByteString(record.value)
        }
        .via(
          InMessageFlow(
            PBEventMetadata,
            ProtocolMessageMap.eventConsumerMessageMap
          )
        )
        .map { msg =>
          ctx.self ! EventReceived(msg)
        }
        .run()

      Behaviors.receiveMessage {
        case EventReceived(msg) => {
          ctx.log.debug(s"$playerId: Event received: $msg")
          player ! Player.GameEventConsumerCommand(
            eventCommandFromEventMessage(msg),
            ctx.self
          )
          Behaviors.same
        }
        case Start() => {
          ctx.log.info(
            s"Starting consumer for ${player}"
          )
          Behaviors.same
        }
      }
    })
  }

  private val eventCommandFromEventMessage
      : PartialFunction[GeneratedMessage, Player.EventCommand] = {
    case PBGameEntityState(entityId, position, velocity, equipment, _) =>
      Player.UpdateEntityState(
        entityId,
        GameEntityState(
          Position(
            position.x,
            position.y
          ),
          Velocity(
            velocity.velX,
            velocity.velY
          ),
          Equipment(
            equipment.hat,
            equipment.hair,
            equipment.eyes,
            equipment.glasses,
            equipment.facialHair,
            equipment.body,
            equipment.outfit
          )
        )
      )
    case PBPlayerMessage(entityId, msg, _) =>
      Player.ReceiveMessage(entityId, msg)
    case PBGameEntityDisconnect(entityId, _) => {
      Player.EntityDisconnect(entityId)
    }
  }
}
