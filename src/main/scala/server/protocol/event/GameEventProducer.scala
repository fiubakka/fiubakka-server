package server.protocol.event

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes.SourceWithInstrumented
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import protobuf.event.chat.message.PBPlayerMessage
import protobuf.event.metadata.PBEventMessageType
import protobuf.event.metadata.PBEventMetadata
import protobuf.event.state.game_entity_disconnect.PBGameEntityDisconnect
import protobuf.event.state.game_entity_state.PBGameEntityEquipment
import protobuf.event.state.game_entity_state.PBGameEntityPosition
import protobuf.event.state.game_entity_state.PBGameEntityState
import protobuf.event.state.game_entity_state.PBGameEntityVelocity
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.structs.PlayerState
import server.protocol.event.kafka.KafkaProducer
import server.protocol.flows.server.protocol.flows.OutMessageFlow

object GameEventProducer {
  sealed trait Command
  final case class PlayerStateUpdate(playerState: PlayerState) extends Command
  final case class AddMessage(msg: String) extends Command
  // This is actually also used anytime the Player should disappear from the map (ie. when starting a Truco match)
  final case class PlayerDisconnect() extends Command

  def apply(playerId: String, partition: Int): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)
      val gameZoneTopic =
        ctx.system.settings.config.getString("game.kafka.topic")

      val config = ctx.system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new ByteArraySerializer)
          .withProducer(KafkaProducer())

      val (conQueue, conSource) = Source
        .queue[GeneratedMessage](1024, OverflowStrategy.dropHead)
        .preMaterialize()

      conSource
        .via(
          OutMessageFlow(
            (length: Int, `type`: GeneratedEnum) =>
              PBEventMetadata(length, `type`.asInstanceOf[PBEventMessageType]),
            ProtocolMessageMap.eventProducerMessageMap
          )
        )
        .map(_.toArray)
        .map(value =>
          new ProducerRecord(gameZoneTopic, partition, playerId, value)
        )
        .instrumentedRunWith(Producer.plainSink(producerSettings))(
          name = "GameEventProducer",
          reportByName = true
        )

      Behaviors.receiveMessage {
        case PlayerStateUpdate(playerState) => {
          ctx.log.debug(s"$playerId: Producing event: $playerState")
          conQueue.offer(
            PBGameEntityState(
              playerId,
              PBGameEntityPosition(
                playerState.dState.position.x,
                playerState.dState.position.y
              ),
              PBGameEntityVelocity(
                playerState.tState.velocity.x,
                playerState.tState.velocity.y
              ),
              PBGameEntityEquipment(
                playerState.dState.equipment.hat,
                playerState.dState.equipment.hair,
                playerState.dState.equipment.eyes,
                playerState.dState.equipment.glasses,
                playerState.dState.equipment.facialHair,
                playerState.dState.equipment.body,
                playerState.dState.equipment.outfit
              )
            )
          )
          Behaviors.same
        }
        case AddMessage(msg) => {
          ctx.log.info(s"$playerId: Adding message: $msg")
          conQueue.offer(PBPlayerMessage(playerId, msg))
          Behaviors.same
        }
        case PlayerDisconnect() => {
          conQueue.offer(PBGameEntityDisconnect(playerId))
          Behaviors.same
        }
      }
    })
  }
}
