package server.protocol.flows

package server.protocol.flows

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage

import java.nio.ByteBuffer

object OutMessageFlow {
  def apply(
      metadataBuilder: Function2[
        Int,
        GeneratedEnum,
        GeneratedMessage { def length: Int; def `type`: GeneratedEnum }
      ],
      protocolMessageMap: Map[String, GeneratedEnum]
  ) = {
    Flow[GeneratedMessage]
      .map { msg =>
        val msgBytes = msg.toByteArray
        // We add an extra $ at the end of the class name to get the companion object name
        // which is the one that is actually mapped
        val metadataBytes = metadataBuilder(
          msgBytes.length,
          protocolMessageMap(s"${msg.getClass.toString}$$")
        ).toByteArray
        // 4 bytes for the Metadata length bytes
        val frameSize = 4 + metadataBytes.length + msgBytes.length
        ByteBuffer
          .allocate(4 + frameSize)
          .order(java.nio.ByteOrder.BIG_ENDIAN)
          .putInt(frameSize)
          .putInt(metadataBytes.length)
          .put(metadataBytes)
          .put(msgBytes)
          .array()
      }
      .map {
        ByteString(_)
      }
  }
}
