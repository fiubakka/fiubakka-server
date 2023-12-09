package server.protocol.client

import protobuf.client.chat.message.PBPlayerMessage
import protobuf.client.init.player_init.PBPlayerInit
import protobuf.client.metadata.PBClientMessageType
import protobuf.client.movement.player_velocity.PBPlayerVelocity
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.position.player_position.PBPlayerPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val clientMessageMap: Map[PBClientMessageType, GeneratedMessageCompanion[
    _ <: GeneratedMessage
  ]] =
    Map(
      PBClientMessageType.PBPlayerInit -> PBPlayerInit,
      PBClientMessageType.PBPlayerVelocity -> PBPlayerVelocity,
      PBClientMessageType.PBPlayerMessage -> PBPlayerMessage
    )

  val serverMessageMap: Map[String, PBServerMessageType] =
    Map(
      PBPlayerPosition.getClass.toString -> PBServerMessageType.PBPlayerPosition,
      PBGameEntityState.getClass.toString -> PBServerMessageType.PBGameEntityState
    )
}
