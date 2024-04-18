package server.domain.entities.player.command

import akka.serialization.jackson.CborSerializable
import server.domain.structs.GameEntityState

object PlayerEventCommand {
  sealed trait EventCommand extends CborSerializable

  final case class ReceiveMessage(
      entityId: String,
      msg: String
  ) extends EventCommand
  final case class UpdateEntityState(
      entityId: String,
      newEntityState: GameEntityState
  ) extends EventCommand
  final case class EntityDisconnect(
      entityId: String
  ) extends EventCommand
}
