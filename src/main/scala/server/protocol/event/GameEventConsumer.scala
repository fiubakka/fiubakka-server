package server.protocol.event

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import protobuf.event.chat.message.PBPlayerMessage
import protobuf.event.metadata.PBEventMetadata
import protobuf.event.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import server.domain.entities.Player
import server.domain.structs.GameEntityState
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.protocol.flows.InMessageFlow

object GameEventConsumer {
  sealed trait Command extends CborSerializable
  final case class EventReceived(msg: GeneratedMessage) extends Command
  final case class Start() extends Command

  def apply(
      playerId: String,
      player: ActorRef[Player.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      Consumer
        .plainSource(
          ConsumerSettings(
            ctx.system.settings.config.getConfig("akka.kafka-consumer"),
            new StringDeserializer,
            new ByteArrayDeserializer
          )
            .withGroupId(playerId),
          Subscriptions.topics("game-zone")
        )
        .filter(record =>
          (record.key == null || record.key != playerId)
        ) // Ignore messages from myself
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
        .runWith(Sink.ignore)

      Behaviors.receiveMessage {
        case EventReceived(msg) => {
          ctx.log.info(s"$playerId: Event received: $msg")
          player ! commandFromEventMessage(msg)
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

  private val commandFromEventMessage
      : PartialFunction[GeneratedMessage, Player.Command] = {
    case PBGameEntityState(entityId, position, velocity, _) =>
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
          )
        )
      )
    case PBPlayerMessage(entityId, msg, _) =>
      Player.ReceiveMessage(entityId, msg)
  }
}
