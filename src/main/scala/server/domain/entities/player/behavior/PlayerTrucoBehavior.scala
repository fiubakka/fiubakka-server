package server.domain.entities.player.behavior

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerActionCommand._
import server.domain.entities.player.command.PlayerReplyCommand._
import server.domain.entities.player.utils.PlayerUtils
import server.domain.entities.truco.TrucoManager
import server.domain.entities.truco.command.TrucoManagerReplyCommand._
import server.domain.structs.PlayerState

object PlayerTrucoBehavior {
  def apply(
      state: PlayerState,
      trucoManager: ActorRef[TrucoManager.Command]
  ): Behavior[Player.Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case SyncTrucoMatchStart(trucoManager) => {
          ctx.log.info(
            "Resending handshake confirmation to TrucoManager!"
          )
          trucoManager ! TrucoManager.PlayerSyncedTrucoMatchStart(
            state.dState.playerName
          )
          Behaviors.same
        }

        case TrucoMatchPlay(playId, play) => {
          trucoManager ! TrucoManager.MakePlay(
            state.dState.playerName,
            playId,
            play
          )
          Behaviors.same
        }

        case TrucoMatchAckPlay(playId) => {
          trucoManager ! TrucoManager.AckPlay(
            state.dState.playerName,
            playId
          )
          Behaviors.same
        }

        case TrucoPlayStateInfo(playState) => {
          ctx.log.info(s"Received truco play state info: $playState")
          state.tState.handler ! NotifyTrucoPlayStateInfo(playState)
          Behaviors.same
        }

        case TrucoAllowPlay(playId) => {
          state.tState.handler ! NotifyTrucoAllowPlay(playId)
          Behaviors.same
        }

        case heartMessage @ (Heartbeat(_) | CheckHeartbeat()) => {
          PlayerUtils.handleHeartbeatMessage(
            ctx,
            heartMessage,
            state,
            (newState) => apply(newState, trucoManager),
            notifyStateToProducer = false
          )
        }

        case _ => Behaviors.same
      }
    }
  }
}
