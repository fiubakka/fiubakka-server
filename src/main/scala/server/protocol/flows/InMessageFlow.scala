package server.protocol.flows

import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

import scala.reflect.Selectable.reflectiveSelectable

object InMessageFlow {
  def apply[A <: GeneratedEnum, B <: GeneratedMessageCompanion[
    ? <: GeneratedMessage { def length: Int; def `type`: A }
  ]](
      companion: B,
      protocolMessageMap: Map[A, GeneratedMessageCompanion[
        ? <: GeneratedMessage
      ]]
  ) = {
    Flow[ByteString]
      .via(
        Framing.lengthField(
          fieldLength = 4,
          maximumFrameLength = 65535,
          byteOrder = java.nio.ByteOrder.BIG_ENDIAN
        )
      )
      .map { messageBytes =>
        try {
          val msgBytes = messageBytes.drop(
            4
          ) // Ignore the length field from the frame, we don't need it
          val metadataSize =
            msgBytes.take(4).iterator.getInt(java.nio.ByteOrder.BIG_ENDIAN)
          val metadata =
            companion.parseFrom(
              msgBytes.drop(4).take(metadataSize).toArray
            )
          val (messageSize, messageType) = (metadata.length, metadata.`type`)
          Some(
            protocolMessageMap(messageType)
              .parseFrom(
                msgBytes.drop(4 + metadataSize).take(messageSize).toArray
              )
          )
        } catch {
          case _: Throwable => None
        }
      }
      .collect { case Some(msg) => msg }
  }
}
