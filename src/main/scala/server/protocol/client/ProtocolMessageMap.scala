package server.protocol.client

import protobuf.client.chat.message.{PBPlayerMessage => PBClientPlayerMessage}
import protobuf.client.init.player_login.PBPlayerLogin
import protobuf.client.init.player_register.PBPlayerRegister
import protobuf.client.metadata.PBClientMessageType
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.server.chat.message.{PBPlayerMessage => PBServerPlayerMessage}
import protobuf.server.init.player_init.PBPlayerInitError
import protobuf.server.init.player_init.PBPlayerInitSuccess
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val clientMessageMap: Map[PBClientMessageType, GeneratedMessageCompanion[
    _ <: GeneratedMessage
  ]] =
    Map(
      PBClientMessageType.PBPlayerLogin -> PBPlayerLogin,
      PBClientMessageType.PBPlayerRegister -> PBPlayerRegister,
      PBClientMessageType.PBPlayerMovement -> PBPlayerMovement,
      PBClientMessageType.PBPlayerMessage -> PBClientPlayerMessage
    )

  val serverMessageMap: Map[String, PBServerMessageType] =
    Map(
      PBPlayerInitError.getClass.toString -> PBServerMessageType.PBPlayerInitError,
      PBPlayerInitSuccess.getClass.toString -> PBServerMessageType.PBPlayerInitSuccess,
      PBGameEntityState.getClass.toString -> PBServerMessageType.PBGameEntityState,
      PBServerPlayerMessage.getClass.toString -> PBServerMessageType.PBPlayerMessage
    )
}
