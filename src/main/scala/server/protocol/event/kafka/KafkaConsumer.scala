package server.protocol.event.kafka

import _root_.server.GameServer
import akka.NotUsed
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.ConsumerSettings
import akka.kafka.KafkaConsumerActor
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

object KafkaConsumer {
  private var consumers
      : Option[Array[Source[ConsumerRecord[String, Array[Byte]], NotUsed]]] =
    None

  def apply(
      partition: Int
  ): Source[ConsumerRecord[String, Array[Byte]], NotUsed] = {
    consumers.getOrElse(Array.empty) match {
      case array if array.nonEmpty => consumers.get(partition)
      case _ =>
        throw new IllegalStateException(
          "Kafka Consumer not initialized. Call configure method first."
        )
    }
  }

  def configure(ctx: ActorContext[GameServer.Command]) = {
    implicit val system = ctx.system

    val kafkaConsumer = ctx.actorOf(
      KafkaConsumerActor.props(
        ConsumerSettings(
          ctx.system.settings.config.getConfig("akka.kafka-consumer"),
          new StringDeserializer,
          new ByteArrayDeserializer
        )
      ),
      "kafka-consumer"
    )

    consumers = Some((0 until 2).map { partition =>
      Consumer
        .plainExternalSource[String, Array[Byte]](
          kafkaConsumer,
          Subscriptions.assignment(new TopicPartition("game-zone", partition))
        )
        .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.right)
        .run()
    }.toArray)
  }
}
