package server.domain.entities.player.command

import akka.serialization.jackson.CborSerializable
import server.domain.structs.DurablePlayerState
import server.domain.structs.GameEntityState

object PlayerReplyCommand {
  sealed trait Command extends CborSerializable

  final case class NotifyEntityStateUpdate(
      entityId: String,
      newEntityState: GameEntityState
  ) extends Command
  final case class NotifyMessageReceived(
      entityId: String,
      msg: String
  ) extends Command
  final case class NotifyEntityDisconnect(
      entityId: String
  ) extends Command
  final case class ReplyStop() extends Command
  final case class Ready(initialState: DurablePlayerState) extends Command
  final case class ChangeMapReady(newMapId: Int) extends Command
  final case class NotifyAskBeginTrucoMatch(opponentUsername: String)
      extends Command
  final case class NotifyBeginTrucoMatchDenied(opponentUsername: String)
      extends Command
}
