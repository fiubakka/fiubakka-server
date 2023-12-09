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
import protobuf.event.metadata.PBEventMessageType
import protobuf.event.metadata.PBEventMetadata
import protobuf.event.state.game_entity_state.PBGameEntityPosition
import protobuf.event.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.structs.DurablePlayerState
import server.protocol.flows.server.protocol.flows.OutMessageFlow

object GameEventProducer {
  sealed trait Command
  final case class PlayerStateUpdate(playerState: DurablePlayerState)
      extends Command

  def apply(playerId: String): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      val config = ctx.system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new ByteArraySerializer)

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
          ctx.log.info(s"$playerId: Producing event: $playerState")
          conQueue.offer(
            PBGameEntityState(
              playerId,
              PBGameEntityPosition(
                playerState.position.x,
                playerState.position.y
              )
            )
          )
          Behaviors.same
        }
      }
    })
  }
}
