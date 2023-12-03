package server.protocol

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
import protobuf.player.PlayerState.{PlayerState => ProtoPlayerState}
import protobuf.player.PlayerState.{Position => ProtoPosition}
import server.domain.structs.PlayerState

object GameEventProducer {
  sealed trait Command
  final case class PlayerStateUpdate(playerState: PlayerState) extends Command

  def apply(playerId: String): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      val config = ctx.system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new ByteArraySerializer)

      val (conQueue, conSource) = Source
        .queue[Array[Byte]](256, OverflowStrategy.backpressure)
        .preMaterialize()

      conSource
        .map(value =>
          new ProducerRecord[String, Array[Byte]]("game-zone", value)
        )
        .runWith(Producer.plainSink(producerSettings))

      Behaviors.receiveMessage {
        case PlayerStateUpdate(playerState) => {
          ctx.log.info(s"$playerId: Producing event: $playerState")
          conQueue.offer(
            ProtoPlayerState(
              playerId,
              ProtoPosition(
                playerState.position.x,
                playerState.position.y
              )
            ).toByteArray
          )
          Behaviors.same
        }
      }
    })
  }
}
