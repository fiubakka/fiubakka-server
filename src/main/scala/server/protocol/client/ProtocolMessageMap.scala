package server.protocol.client

import protobuf.client.init.player_init.PBPlayerInit
import protobuf.client.metadata.PBClientMessageType
import protobuf.client.movement.player_velocity.PBPlayerVelocity
import protobuf.event.state.game_entity_state.PBGameEntityState
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.position.player_position.PBPlayerPosition
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val clientMessageMap: Map[PBClientMessageType, GeneratedMessageCompanion[
    _ <: GeneratedMessage
  ]] =
    Map(
      PBClientMessageType.PBPlayerInit -> PBPlayerInit,
      PBClientMessageType.PBPlayerVelocity -> PBPlayerVelocity
    )

  val serverMessageMap: Map[String, PBServerMessageType] =
    Map(
      PBPlayerPosition.getClass.toString -> PBServerMessageType.PBPlayerPosition,
      PBGameEntityState.getClass.toString -> PBServerMessageType.PBGameEntityState
    )
}
