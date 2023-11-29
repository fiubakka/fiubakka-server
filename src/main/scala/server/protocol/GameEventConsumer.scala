package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.Sink
import org.apache.kafka.common.serialization.StringDeserializer
import server.domain.entities.Player

object GameEventConsumer {
  sealed trait Command
  final case class EventReceived(msg: String) extends Command

  val TypeKey = EntityTypeKey[Command]("EventConsumer")

  def apply(
      entityId: String,
      player: EntityRef[Player.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val system = ctx.system

      Consumer
        .plainSource(
          ConsumerSettings(
            ctx.system.settings.config.getConfig("akka.kafka-consumer"),
            new StringDeserializer,
            new StringDeserializer
          )
            .withGroupId(entityId),
          Subscriptions.topics("test")
        )
        .map(v => ctx.self ! EventReceived(v.value()))
        .runWith(Sink.ignore)

      Behaviors.receiveMessage {
        case EventReceived(_) => {
          player ! Player.PrintPosition()
          Behaviors.same
        }
      }
    })
  }
}
