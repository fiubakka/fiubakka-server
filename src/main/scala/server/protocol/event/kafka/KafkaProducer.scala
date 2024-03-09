package server.protocol.event.kafka

import akka.actor.typed.ActorSystem
import akka.kafka.ProducerSettings
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

object KafkaProducer {
  private var producer: Option[Producer[String, Array[Byte]]] = None

  def apply(): Producer[String, Array[Byte]] = {
    producer.getOrElse(
      throw new IllegalStateException(
        "Kafka Producer not initialized. Call configure method first."
      )
    )
  }

  def configure(system: ActorSystem[_]) = {
    producer = Some(
      ProducerSettings(
        system.settings.config.getConfig("akka.kafka-producer"),
        new StringSerializer,
        new ByteArraySerializer
      ).createKafkaProducer()
    )
  }
}
