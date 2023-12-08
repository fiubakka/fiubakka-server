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
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import protobuf.event.state.game_entity_state.PBGameEntityState
import server.domain.entities.Player
import server.domain.structs.GameEntityPosition
import server.domain.structs.GameEntityState

object GameEventConsumer {
  sealed trait Command extends CborSerializable
  final case class EventReceived(entityState: PBGameEntityState) extends Command
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
        .map(record => PBGameEntityState.parseFrom(record.value()))
        .filter(e => e.entityId != playerId) // Ignore messages from myself
        .map(e => ctx.self ! EventReceived(e))
        .runWith(Sink.ignore)

      Behaviors.receiveMessage {
        case EventReceived(msg) => {
          ctx.log.info(s"$playerId: Event received: $msg")
          player ! Player.UpdateEntityState(
            msg.entityId,
            GameEntityState(
              GameEntityPosition(
                msg.position.x,
                msg.position.y
              )
            )
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
}
