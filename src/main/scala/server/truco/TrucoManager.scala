package server.truco

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerCommand
import server.domain.structs.truco.TrucoManagerPlayerState
import server.domain.structs.truco.TrucoManagerState
import server.domain.structs.truco.TrucoPlay
import server.domain.truco.TrucoMatch
import server.sharding.Sharding

import scala.concurrent.duration._

object TrucoManager {
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

  def apply(
      firstPlayerName: String,
      secondPlayerName: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.log.info(
        "Starting TrucoManager for players {} and {}",
        firstPlayerName,
        secondPlayerName
      )

      val sharding = Sharding()

      val state = TrucoManagerState(
        firstPlayer = TrucoManagerPlayerState(
          sharding.entityRefFor(Player.TypeKey, firstPlayerName),
          playerName = firstPlayerName,
          hasInit = false
        ),
        secondPlayer = TrucoManagerPlayerState(
          sharding.entityRefFor(Player.TypeKey, secondPlayerName),
          playerName = secondPlayerName,
          hasInit = false
        ),
        trucoMatch = new TrucoMatch(),
        playId = 0
      )

      initBehavior(state)
    }
  }

  def initBehavior(state: TrucoManagerState): Behavior[Command] = {
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
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case AskPlayersToStartMatch() => {
            if !state.firstPlayer.hasInit then {
              state.firstPlayer.player ! PlayerCommand.SyncTrucoMatchStart(
                ctx.self
              )
            }
            if !state.secondPlayer.hasInit then {
              state.secondPlayer.player ! PlayerCommand.SyncTrucoMatchStart(
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
              runningBehavior(
                newState
              ) // Setup ready, both players accepted the match
            } else {
              initBehavior(newState)
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

  def runningBehavior(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      Behaviors.same // TODO Implement MakePlay
    }
  }
}
