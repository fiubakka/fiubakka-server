package server.domain.entities.player.command

import akka.serialization.jackson.CborSerializable
import server.domain.structs.DurablePlayerState
import server.domain.structs.GameEntityState
import server.protocol.truco.TrucoPlayState

object PlayerReplyCommand {
  sealed trait ReplyCommand extends CborSerializable

  final case class NotifyEntityStateUpdate(
      entityId: String,
      newEntityState: GameEntityState
  ) extends ReplyCommand
  final case class NotifyMessageReceived(
      entityId: String,
      msg: String
  ) extends ReplyCommand
  final case class NotifyEntityDisconnect(
      entityId: String
  ) extends ReplyCommand
  final case class ReplyStop() extends ReplyCommand
  final case class Ready(initialState: DurablePlayerState) extends ReplyCommand
  final case class ChangeMapReady(newMapId: Int) extends ReplyCommand
  final case class NotifyAskBeginTrucoMatch(
      opponentUsername: String
  ) extends ReplyCommand
  final case class NotifyBeginTrucoMatchDenied(
      opponentUsername: String
  ) extends ReplyCommand
  final case class NotifyTrucoPlayStateInfo(
      playState: TrucoPlayState
  ) extends ReplyCommand
}
