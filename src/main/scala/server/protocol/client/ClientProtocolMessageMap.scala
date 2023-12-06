package server.protocol.client

import protobuf.common.metadata.PBMessageType
import protobuf.init.player_init.PBPlayerInit
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ClientProtocolMessageMap {
  val messageMap
      : Map[PBMessageType, GeneratedMessageCompanion[_ <: GeneratedMessage]] =
    Map(
      PBMessageType.PBPlayerInit -> PBPlayerInit
    )
}
