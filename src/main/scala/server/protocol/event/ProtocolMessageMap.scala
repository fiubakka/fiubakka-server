package server.protocol.event

import protobuf.event.metadata.PBEventMessageType
import protobuf.event.state.game_entity_state.PBGameEntityState
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

object ProtocolMessageMap {
  val eventConsumerMessageMap
      : Map[PBEventMessageType, GeneratedMessageCompanion[
        _ <: GeneratedMessage
      ]] =
    Map(
      PBEventMessageType.PBGameEntityState -> PBGameEntityState
    )

  val eventProducerMessageMap: Map[String, PBEventMessageType] =
    Map(
      PBGameEntityState.getClass.toString -> PBEventMessageType.PBGameEntityState
    )
}
