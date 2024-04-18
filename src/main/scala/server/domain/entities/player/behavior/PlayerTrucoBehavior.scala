package server.domain.entities.player.behavior

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerCommand._
import server.domain.structs.PlayerState
import server.truco.TrucoManager

object PlayerTrucoBehavior {
  def apply(
      state: PlayerState,
      trucoManager: ActorRef[TrucoManager.Command]
  ): Behavior[Player.Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case TrucoMatchPlay(playId, play) => {
          trucoManager ! TrucoManager.MakePlay(
            state.dState.playerName,
            playId,
            play
          )
          Behaviors.same
        }

        case _ => Behaviors.same
      }
    }
  }
}
