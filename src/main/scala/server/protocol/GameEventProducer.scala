package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import server.domain.entities.Player

object GameEventProducer {
  sealed trait Command
  final case class ProduceEvent(msg: String) extends Command

  val TypeKey = EntityTypeKey[Command]("GameEventProducer")

  def apply(
      entityId: String,
      player: EntityRef[
        Player.Command
      ] // TODO: Necesitamos el player en el producer?
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      val config = ctx.system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new StringSerializer)

      val (conQueue, conSource) = Source
        .queue[String](256, OverflowStrategy.backpressure)
        .preMaterialize()

      conSource
        .map(value => new ProducerRecord[String, String]("game-zone", value))
        .runWith(Producer.plainSink(producerSettings))

      Behaviors.receiveMessage {
        case ProduceEvent(msg) => {
          ctx.log.info(s"Producing event: $msg")
          conQueue.offer(msg)
          Behaviors.same
        }
      }
    })
  }
}
