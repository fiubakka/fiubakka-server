package server.protocol.event

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Sink
import akka.stream.typed.scaladsl.ActorSink
import akka.util.ByteString
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes.SourceWithInstrumented
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

  sealed trait Ack extends CborSerializable
  object Ack extends Ack

  def apply(
      player: ActorRef[Player.Command],
      partition: Int
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)
      val playerId =
        player.path.name // The Player Entity Id is its Actor's name

      val playerSink: Sink[Player.EventCommand, NotUsed] =
        ActorSink.actorRefWithBackpressure(
          ref = player,
          messageAdapter = (responseRef: ActorRef[Ack], gameEvent) =>
            Player.GameEventConsumerCommand(
              gameEvent,
              ctx.self,
              responseRef
            ),
          onInitMessage = (responseRef: ActorRef[Ack]) =>
            Player.GameEventConsumerReady(responseRef, partition),
          ackMessage = Ack,
          onCompleteMessage = Player.GameEventConsumerFailure(
            ctx.self,
            "Stream completed when it's not supposed to"
          ),
          onFailureMessage = (error) =>
            Player.GameEventConsumerFailure(ctx.self, error.getMessage())
        )

      KafkaConsumer(partition)
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
        .map { eventCommandFromEventMessage }
        .instrumentedRunWith(playerSink)(
          name = "GameEventConsumer",
          reportByName = true
        )

      Behaviors.empty
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
