package server.domain.entities.player.command

import akka.actor.typed.ActorRef
import akka.serialization.jackson.CborSerializable
import server.domain.entities.player.Player
import server.domain.structs.DurablePlayerState
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.domain.structs.truco.TrucoMatchChallengeReplyEnum
import server.domain.structs.truco.TrucoPlay
import server.protocol.event.GameEventConsumer
import server.truco.TrucoManager

object PlayerCommand {
  sealed trait Command extends CborSerializable

  final case class Init(initialData: Player.InitData) extends Command

  // We also send to PlayerHandler ref in case the Player switches cluster nodes or dies
  // and starts again. In this case we would be stuck waiting for an Init message from the
  // PlayerHandler that would never arrive! By relying on this fallback message, we fix this issue.
  //
  // We still keep the Init message to avoid including other init data (ie. the equipment) being included
  // in the Heartbeat message and having a more complex message structure.
  final case class Heartbeat(handler: ActorRef[PlayerReplyCommand.ReplyCommand])
      extends Command
  final case class CheckHeartbeat() extends Command

  final case class Stop() extends Command
  final case class StopReady() extends Command

  final case class InitialState(
      initialState: DurablePlayerState
  ) extends Command

  final case class Move(
      velocity: Velocity,
      position: Position
  ) extends Command

  final case class PersistState() extends Command

  final case class AddMessage(
      msg: String
  ) extends Command
  final case class UpdateEquipment(
      equipment: Equipment
  ) extends Command
  final case class ChangeMap(
      newMapId: Int
  ) extends Command

  final case class GameEventConsumerCommand(
      command: PlayerEventCommand.EventCommand,
      consumerRef: ActorRef[GameEventConsumer.Command]
  ) extends Command

  // Truco messages

  final case class BeginTrucoMatch(opponentUsername: String) extends Command
  final case class AskBeginTrucoMatch(opponentUsername: String) extends Command
  final case class BeginTrucoMatchDenied(opponentUsername: String)
      extends Command
  final case class ReplyBeginTrucoMatch(
      opponentUsername: String,
      replyStatus: TrucoMatchChallengeReplyEnum
  ) extends Command
  final case class SyncTrucoMatchStart(
      trucoManager: ActorRef[TrucoManager.Command]
  ) extends Command
  final case class TrucoMatchPlay(playId: Int, play: TrucoPlay) extends Command
}
