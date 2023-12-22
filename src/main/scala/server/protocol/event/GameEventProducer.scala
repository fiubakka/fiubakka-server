package server.protocol.event

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import protobuf.event.chat.message.PBPlayerMessage
import protobuf.event.metadata.PBEventMessageType
import protobuf.event.metadata.PBEventMetadata
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

  def apply(playerId: String): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      val config = ctx.system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new ByteArraySerializer)
          .withProducer(KafkaProducer())

      val (conQueue, conSource) = Source
        .queue[GeneratedMessage](256, OverflowStrategy.backpressure)
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
        .map(value => new ProducerRecord("game-zone", playerId, value))
        .runWith(Producer.plainSink(producerSettings))

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
      }
    })
  }
}
