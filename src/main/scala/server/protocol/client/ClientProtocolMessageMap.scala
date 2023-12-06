package server.protocol.client

import protobuf.client.init.player_init.PBPlayerInit
import protobuf.client.metadata.PBClientMessageType
import protobuf.client.movement.player_velocity.PBPlayerVelocity
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ClientProtocolMessageMap {
  val messageMap: Map[PBClientMessageType, GeneratedMessageCompanion[
    _ <: GeneratedMessage
  ]] =
    Map(
      PBClientMessageType.PBPlayerInit -> PBPlayerInit,
      PBClientMessageType.PBPlayerVelocity -> PBPlayerVelocity
    )
}
