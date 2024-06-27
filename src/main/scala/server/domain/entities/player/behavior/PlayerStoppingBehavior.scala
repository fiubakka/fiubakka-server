package server.domain.entities.player.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerActionCommand.*

object PlayerStoppingBehavior {
  def apply(): Behavior[Player.Command] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case StopReady() => {
          Behaviors.stopped
        }

        case _ => {
          Behaviors.same
        }
      }
    }
  }
}
