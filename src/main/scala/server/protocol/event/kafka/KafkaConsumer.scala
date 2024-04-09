package server.protocol.event.kafka

import akka.NotUsed
import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.typed.Cluster
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import server.GameServer

object KafkaConsumer {
  private var consumers
      : Option[List[Source[ConsumerRecord[String, Array[Byte]], NotUsed]]] =
    None

  def apply(
      partition: Int
  ): Source[ConsumerRecord[String, Array[Byte]], NotUsed] = {
    consumers match {
      case Some(consumers) =>
        consumers.lift(partition).get // Should always exist
      case None =>
        throw new IllegalStateException(
          "Kafka Consumer not initialized. Call configure method first."
        )
    }
  }

  // We are not using a single underlying KafkaConsumer for different Consumer.plainExternalSource
  // because the performance is terrible. Instead, we manually filter the messages
  // based on their partition for each broadcasted source.
  def configure(ctx: ActorContext[GameServer.Command]) = {
    implicit val system = ctx.system
    val nodeId =
      Cluster(
        ctx.system
      ).selfMember.uniqueAddress.longUid // Node Id in the Akka Cluster

    val gameZoneTopic = ctx.system.settings.config.getString("game.kafka.topic")
    val gameZonePartitions =
      ctx.system.settings.config.getInt("game.kafka.partitions")
    val groupPrefix =
      ctx.system.settings.config.getString("game.kafka.consumer.group-prefix")

    val kafkaConsumerSource = Consumer
      .plainSource(
        ConsumerSettings(
          ctx.system.settings.config.getConfig("akka.kafka-consumer"),
          new StringDeserializer,
          new ByteArrayDeserializer
        )
          .withGroupId(
            s"$groupPrefix-$nodeId"
          ), // Each node should read every message from the topic
        Subscriptions.topics(gameZoneTopic)
      )
      .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.right)
      .run()

    consumers = Some(
      (0 until gameZonePartitions).map { partition =>
        kafkaConsumerSource
          .filter(_.partition == partition)
          .buffer(2048, OverflowStrategy.dropBuffer)
          .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.right)
          .run()
      }.toList
    )

    // There seems to be a bug with BroadcastHub where specifying startAfterNrOfConsumers = 0
    // does not start the broadcast. It still needs 1 consumer to be connected to start.
    // Do note that while we are not specifying the startAfterNrOfConsumers explicitly, the default is 0.
    // To solve this, we force a dummy consumer.
    (0 until gameZonePartitions).foreach { partition =>
      apply(partition).run()
    }
  }
}
