package server.protocol

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
import protobuf.player.PlayerState.{PlayerState => PlayerStateProto}
import server.domain.entities.Player

object GameEventConsumer {
  sealed trait Command extends CborSerializable
  final case class EventReceived(playerState: PlayerStateProto) extends Command
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
        .map(record => PlayerStateProto.parseFrom(record.value()))
        .map(v => ctx.self ! EventReceived(v))
        .runWith(Sink.ignore)

      Behaviors.receiveMessage {
        case EventReceived(msg) => {
          ctx.log.info(s"Event received: $msg")
          Behaviors.same
        }
        case Start() => {
          ctx.log.info(s"Starting consumer for ${player}")
          Behaviors.same
        }
      }
    })
  }
}
