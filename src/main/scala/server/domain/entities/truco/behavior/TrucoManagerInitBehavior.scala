package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.truco.command.TrucoManagerCommand._
import server.domain.structs.truco.TrucoManagerState

import scala.concurrent.duration._

object TrucoManagerInitBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(
        "startMatchPlayersSync",
        AskPlayersToStartMatch(),
        250.milli
      )
      timers.startTimerAtFixedRate(
        "failMatchPlayersSync",
        FailMatchPlayersSync(),
        10.seconds
      ) // At 10 seconds, stop the Truco match if players don't sync
      behavior(state)
    }
  }

  private def behavior(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case AskPlayersToStartMatch() => {
            if !state.firstPlayer.hasInit then {
              state.firstPlayer.player ! Player.SyncTrucoMatchStart(
                ctx.self
              )
            }
            if !state.secondPlayer.hasInit then {
              state.secondPlayer.player ! Player.SyncTrucoMatchStart(
                ctx.self
              )
            }
            Behaviors.same
          }

          case PlayerSyncedTrucoMatchStart(playerName) => {
            val newState = playerName match {
              case state.firstPlayer.playerName => {
                state.copy(
                  firstPlayer = state.firstPlayer.copy(hasInit = true)
                )
              }
              case state.secondPlayer.playerName => {
                state.copy(
                  secondPlayer = state.secondPlayer.copy(hasInit = true)
                )
              }
            }

            if newState.firstPlayer.hasInit && newState.secondPlayer.hasInit
            then {
              timers.cancel("failMatchPlayersSync")
              ctx.log.info("Both players synced, starting Truco match")
              TrucoManagerPlayAckBehavior(
                newState
              ) // Setup ready, both players accepted the match
            } else {
              apply(newState)
            }
          }

          case FailMatchPlayersSync() => {
            ctx.log.error(
              "Truco match failed to start, players didn't sync in time! Stopping TrucoManager"
            )
            Behaviors.stopped // TODO send match aborted to optimize player returning to lobby
          }

          case _ => Behaviors.same
        }
      }
    }
  }
}
