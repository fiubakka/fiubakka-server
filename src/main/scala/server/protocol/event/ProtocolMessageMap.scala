package server.protocol.event

import protobuf.event.chat.message.PBPlayerMessage
import protobuf.event.metadata.PBEventMessageType
import protobuf.event.state.game_entity_disconnect.PBGameEntityDisconnect
import protobuf.event.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val eventConsumerMessageMap
      : Map[PBEventMessageType, GeneratedMessageCompanion[
        ? <: GeneratedMessage
      ]] =
    Map(
      PBEventMessageType.PBGameEntityState -> PBGameEntityState,
      PBEventMessageType.PBPlayerMessage -> PBPlayerMessage,
      PBEventMessageType.PBGameEntityDisconnect -> PBGameEntityDisconnect
    )

  val eventProducerMessageMap: Map[String, PBEventMessageType] =
    Map(
      PBGameEntityState.getClass.toString -> PBEventMessageType.PBGameEntityState,
      PBPlayerMessage.getClass.toString -> PBEventMessageType.PBPlayerMessage,
      PBGameEntityDisconnect.getClass.toString -> PBEventMessageType.PBGameEntityDisconnect
    )
}
