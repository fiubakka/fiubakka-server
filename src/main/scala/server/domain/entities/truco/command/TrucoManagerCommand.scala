package server.domain.entities.truco.command

import akka.serialization.jackson.CborSerializable
import server.domain.structs.truco.TrucoPlay

object TrucoManagerCommand {
  sealed trait Command extends CborSerializable

  // Init / sync
  final case class FailMatchPlayersSync()
      extends Command // Will stop the TrucoManager if players don't sync in time
  final case class AskPlayersToStartMatch() extends Command
  final case class PlayerSyncedTrucoMatchStart(playerName: String)
      extends Command

  // Match
  final case class MakePlay(playerName: String, playId: Int, play: TrucoPlay)
      extends Command
  final case class NotifyPlay() extends Command
}
