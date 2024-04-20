package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.truco.command.TrucoManagerCommand._
import server.domain.structs.truco.TrucoCardPlay
import server.domain.structs.truco.TrucoManagerState
import server.domain.structs.truco.TrucoPlay
import server.domain.structs.truco.TrucoShoutEnum
import server.domain.structs.truco.TrucoShoutPlay

object TrucoManagerRunningBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case MakePlay(playerName, playId, play) => {
          if state.playId >= playId then {
            Behaviors.same // Ignore old plays
          } else {
            ctx.log.info("Player {} made play {}", playerName, play)
            val newState = handlePlay(ctx, state, play)
            apply(newState)
          }
        }

        case _ => Behaviors.same
      }
    }
  }

  private def handlePlay(
      ctx: ActorContext[Command],
      state: TrucoManagerState,
      play: TrucoPlay
  ): TrucoManagerState = {
    try {
      play match {
        case TrucoCardPlay(card) => state.trucoMatch.play(card)
        case TrucoShoutPlay(shout) =>
          state.trucoMatch.shout(TrucoShoutEnum.toShoutPlayEnum(shout))
      }
      val newState = state.copy(
        playId = state.playId + 1
      )
      newState
    } catch {
      case err => {
        ctx.log.error("Error handling play, ignoring it: {}", err)
        state
      }
    }
  }
}
