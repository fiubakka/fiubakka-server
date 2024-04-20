package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.truco.command.TrucoManagerCommand._
import server.domain.entities.truco.command.TrucoManagerReplyCommand._
import server.domain.structs.truco.TrucoCardPlay
import server.domain.structs.truco.TrucoManagerState
import server.domain.structs.truco.TrucoPlay
import server.domain.structs.truco.TrucoShoutEnum
import server.domain.structs.truco.TrucoShoutPlay
import server.domain.truco.cards.Card
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum
import server.protocol.truco.NextPlayInfo
import server.protocol.truco.PlayState
import server.protocol.truco.PlayType

import scala.concurrent.duration._

object TrucoManagerRunningBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay("sendMatchState", NotifyPlay(), 2.seconds)
      behavior(state)
    }
  }

  private def behavior(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case MakePlay(playerName, playId, play) => {
          if state.playId >= playId then {
            Behaviors.same // Ignore old plays
          } else {
            ctx.log.info("Player {} made play {}", playerName, play)
            val newState = handlePlay(ctx, state, play)
            ctx.self ! NotifyPlay() // Optimization to reduce latency
            behavior(newState)
          }
        }

        case NotifyPlay() => {
          val playStateInfo = getPlayStateInfo(state)
          state.firstPlayer.player ! PlayStateInfo(playStateInfo)
          state.secondPlayer.player ! PlayStateInfo(playStateInfo)
          Behaviors.same
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

  private def getPlayStateInfo(state: TrucoManagerState): PlayState = {
    val lastPlay = state.trucoMatch.getLastPlay()
    PlayState(
      playId = state.playId,
      playType = lastPlay match {
        case _: Card => PlayType.Card
        case _       => PlayType.Shout
      },
      isGameOver = true, // TODO
      isMatchOver = true, // TODO
      card =
        Option.when(lastPlay.isInstanceOf[Card])(lastPlay.asInstanceOf[Card]),
      shout = Option.when(!lastPlay.isInstanceOf[Card])(
        TrucoShoutEnum.fromShoutPlayEnum(
          lastPlay.asInstanceOf[EnvidoEnum | TrucoEnum]
        )
      ),
      nextPlayInfo = Some(
        NextPlayInfo(
          nextPlayer = "foo", // TODO
          isPlayCardAvailable = true, // TODO
          availableShouts = Seq(TrucoShoutEnum.Envido)
        )
      ) // TODO
    )
  }
}
