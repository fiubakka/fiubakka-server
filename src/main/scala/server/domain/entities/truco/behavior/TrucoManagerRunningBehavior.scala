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

import scala.concurrent.duration._

object TrucoManagerRunningBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(
          "sendAllowPlay",
          NotifyAllowPlay(),
          2.seconds
        )
        ctx.self ! NotifyAllowPlay() // Optimization to avoid waiting 2 seconds for first message
        behavior(state)
      }
    }
  }

  private def behavior(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case MakePlay(playerName, playId, play) => {
            if state.playId >= playId then {
              ctx.log.info(
                "Ignoring old play with playId {} from player {}",
                playId,
                playerName
              )
              Behaviors.same // Ignore old plays
            } else {
              ctx.log.info("Player {} made play {}", playerName, play)
              val newState = handlePlay(ctx, state, play)
              ctx.self ! NotifyPlay() // Optimization to reduce latency
              timers.cancel("sendAllowPlay")
              TrucoManagerPlayAckBehavior(newState)
            }
          }

          // We keep notify the corresponding player that he is allowed to play
          // until we receive his play
          case NotifyAllowPlay() => {
            state.trucoMatch.currentPlayer match {
              case state.trucoMatch.firstPlayer =>
                state.firstPlayer.player ! TrucoAllowPlay(state.playId + 1)
              case state.trucoMatch.secondPlayer =>
                state.secondPlayer.player ! TrucoAllowPlay(state.playId + 1)
            }
            Behaviors.same
          }

          case PlayerDisconnected(playerName) => {
            ctx.log.info("Player {} disconnected", playerName)
            if !state.trucoMatch.isMatchOver then { // If the match is over then these messages are not necessary
              playerName match {
                case state.firstPlayer.playerName =>
                  state.secondPlayer.player ! TrucoPlayerDisconnected(
                    state.firstPlayer.playerName
                  )
                case state.secondPlayer.playerName =>
                  state.firstPlayer.player ! TrucoPlayerDisconnected(
                    state.secondPlayer.playerName
                  )
              }
            }
            Behaviors.stopped
          }

          case _ => Behaviors.same
        }
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
        case Left(TrucoCardPlay(card)) => state.trucoMatch.play(card)
        case Right(TrucoShoutPlay(shout)) =>
          shout match {
            case TrucoShoutEnum.Mazo => state.trucoMatch.goToMazo()
            case _ =>
              state.trucoMatch.shout(TrucoShoutEnum.toShoutPlayEnum(shout))
          }
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
