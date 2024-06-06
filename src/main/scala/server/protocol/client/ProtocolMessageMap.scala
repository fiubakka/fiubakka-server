package server.protocol.client

import protobuf.client.chat.message.{PBPlayerMessage => PBClientPlayerMessage}
import protobuf.client.init.player_login.PBPlayerLogin
import protobuf.client.init.player_register.PBPlayerRegister
import protobuf.client.inventory.update_equipment.PBPlayerUpdateEquipment
import protobuf.client.map.change_map.PBPlayerChangeMap
import protobuf.client.metadata.PBClientMessageType
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.client.truco.ack_play.PBTrucoAckPlay
import protobuf.client.truco.disconnect.PBTrucoDisconnect
import protobuf.client.truco.match_challenge.PBTrucoMatchChallenge
import protobuf.client.truco.match_challenge_reply.PBTrucoMatchChallengeReply
import protobuf.client.truco.play.{PBTrucoPlay => PBClientTrucoPlay}
import protobuf.server.chat.message.{PBPlayerMessage => PBServerPlayerMessage}
import protobuf.server.init.player_init.PBPlayerInitError
import protobuf.server.init.player_init.PBPlayerInitSuccess
import protobuf.server.map.change_map_ready.PBPlayerChangeMapReady
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.state.game_entity_disconnect.PBGameEntityDisconnect
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.truco.allow_play.PBTrucoAllowPlay
import protobuf.server.truco.match_challenge_denied.PBTrucoMatchChallengeDenied
import protobuf.server.truco.match_challenge_request.PBTrucoMatchChallengeRequest
import protobuf.server.truco.play.{PBTrucoPlay => PBServerTrucoPlay}
import protobuf.server.truco.player_disconnected.PBTrucoPlayerDisconnected
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val clientMessageMap: Map[PBClientMessageType, GeneratedMessageCompanion[
    ? <: GeneratedMessage
  ]] =
    Map(
      PBClientMessageType.PBPlayerLogin -> PBPlayerLogin,
      PBClientMessageType.PBPlayerRegister -> PBPlayerRegister,
      PBClientMessageType.PBPlayerMovement -> PBPlayerMovement,
      PBClientMessageType.PBPlayerMessage -> PBClientPlayerMessage,
      PBClientMessageType.PBPlayerChangeMap -> PBPlayerChangeMap,
      PBClientMessageType.PBPlayerUpdateEquipment -> PBPlayerUpdateEquipment,
      PBClientMessageType.PBTrucoMatchChallenge -> PBTrucoMatchChallenge,
      PBClientMessageType.PBTrucoMatchChallengeReply -> PBTrucoMatchChallengeReply,
      PBClientMessageType.PBTrucoPlay -> PBClientTrucoPlay,
      PBClientMessageType.PBTrucoPlayAck -> PBTrucoAckPlay,
      PBClientMessageType.PBTrucoDisconnect -> PBTrucoDisconnect
    )

  val serverMessageMap: Map[String, PBServerMessageType] =
    Map(
      PBPlayerInitError.getClass.toString -> PBServerMessageType.PBPlayerInitError,
      PBPlayerInitSuccess.getClass.toString -> PBServerMessageType.PBPlayerInitSuccess,
      PBGameEntityState.getClass.toString -> PBServerMessageType.PBGameEntityState,
      PBServerPlayerMessage.getClass.toString -> PBServerMessageType.PBPlayerMessage,
      PBPlayerChangeMapReady.getClass.toString -> PBServerMessageType.PBPlayerChangeMapReady,
      PBGameEntityDisconnect.getClass.toString -> PBServerMessageType.PBGameEntityDisconnect,
      PBTrucoMatchChallengeRequest.getClass.toString -> PBServerMessageType.PBTrucoMatchChallengeRequest,
      PBTrucoMatchChallengeDenied.getClass.toString -> PBServerMessageType.PBTrucoMatchChallengeDenied,
      PBServerTrucoPlay.getClass.toString -> PBServerMessageType.PBTrucoPlay,
      PBTrucoAllowPlay.getClass.toString -> PBServerMessageType.PBTrucoAllowPlay,
      PBTrucoPlayerDisconnected.getClass.toString -> PBServerMessageType.PBTrucoPlayerDisconnected
    )
}
