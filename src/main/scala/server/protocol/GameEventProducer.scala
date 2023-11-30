package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
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
      player: EntityRef[Player.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val system = ctx.system

      val config = system.settings.config.getConfig("akka.kafka-producer")
      val producerSettings =
        ProducerSettings(config, new StringSerializer, new StringSerializer)

      Source(1 to 5)
        .map(_.toString)
        .map(value => new ProducerRecord[String, String]("game-zone", value))
        .runWith(Producer.plainSink(producerSettings))

      Behaviors.receiveMessage {
        case ProduceEvent(msg) => {
          ctx.log.info(s"Producing event: $msg")
          Behaviors.same
        }
      }
    })
  }
}
