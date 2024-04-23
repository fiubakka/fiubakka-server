package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.truco.command.TrucoManagerCommand._
import server.domain.entities.truco.command.TrucoManagerReplyCommand._
import server.domain.structs.truco.TrucoManagerState
import server.domain.structs.truco.TrucoShoutEnum
import server.domain.truco.cards.Card
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum
import server.protocol.truco.NextPlayInfo
import server.protocol.truco.PlayState
import server.protocol.truco.PlayType
import server.protocol.truco.TrucoPoints

import scala.concurrent.duration._

object TrucoManagerPlayAckBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay("sendMatchState", NotifyPlay(), 2.seconds)
      behavior(state, firstPlayerAck = false, secondPlayerAck = false)
    }
  }

  private def behavior(
      state: TrucoManagerState,
      firstPlayerAck: Boolean,
      secondPlayerAck: Boolean
  ): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case AckPlay(playerName, playId) if playId == state.playId => {
            ctx.log.info("Player {} acknowledged play {}", playerName, playId)
            if firstPlayerAck && secondPlayerAck then {
              ctx.log.info("Both player acknowledged play {}", playId)
              timers.cancel("sendMatchState")
              behaviorAfterAck(state)
            } else
              playerName match {
                case state.firstPlayer.playerName =>
                  behavior(state, firstPlayerAck = true, secondPlayerAck)
                case state.secondPlayer.playerName =>
                  behavior(state, firstPlayerAck, secondPlayerAck = true)
              }
          }

          case NotifyPlay() => {
            state.firstPlayer.player ! PlayStateInfo(
              getPlayStateInfoForPlayer(state.firstPlayer.playerName, state)
            )
            state.secondPlayer.player ! PlayStateInfo(
              getPlayStateInfoForPlayer(state.secondPlayer.playerName, state)
            )
            Behaviors.same
          }

          case _ => Behaviors.same
        }
      }
    }
  }

  private def behaviorAfterAck(state: TrucoManagerState): Behavior[Command] = {
    if state.trucoMatch.isGameOver then {
      state.trucoMatch.startNextGame()
      val newState = state.copy(playId = state.playId + 1)
      TrucoManagerPlayAckBehavior(newState)
    } else {
      TrucoManagerRunningBehavior(state)
    }
  }

  private def getPlayStateInfoForPlayer(
      playerName: String,
      state: TrucoManagerState
  ): PlayState = {
    val lastPlay = state.trucoMatch.getLastPlay()
    PlayState(
      playId = state.playId,
      playType = lastPlay match {
        case _: Card => PlayType.Card
        case _       => PlayType.Shout
      }, // TODO this does not make sense if its the first message of each game, as no play has been made yet. Maybe a new play type StartGame?
      playerCards = playerName match {
        case state.firstPlayer.playerName =>
          state.trucoMatch.firstPlayer.hand.cards
        case state.secondPlayer.playerName =>
          state.trucoMatch.secondPlayer.hand.cards
      },
      opponentCardAmount = playerName match {
        case state.firstPlayer.playerName =>
          state.trucoMatch.secondPlayer.hand.cards.size
        case state.secondPlayer.playerName =>
          state.trucoMatch.firstPlayer.hand.cards.size
      },
      firstPlayerPoints = TrucoPoints(
        state.firstPlayer.playerName,
        state.trucoMatch.firstPlayer.points
      ),
      secondPlayerPoints = TrucoPoints(
        state.secondPlayer.playerName,
        state.trucoMatch.secondPlayer.points
      ),
      isGameOver = state.trucoMatch.isGameOver,
      isMatchOver = state.trucoMatch.isMatchOver,
      card =
        Option.when(lastPlay.isInstanceOf[Card])(lastPlay.asInstanceOf[Card]),
      shout = Option.when(!lastPlay.isInstanceOf[Card])(
        TrucoShoutEnum.fromShoutPlayEnum(
          lastPlay.asInstanceOf[EnvidoEnum | TrucoEnum]
        )
      ),
      nextPlayInfo = Some(
        NextPlayInfo(
          nextPlayer = getNextPlayerName(state),
          isPlayCardAvailable = state.trucoMatch.isPlayingCardLegalMove,
          availableShouts = Seq(TrucoShoutEnum.Envido) // TODO
        )
      )
    )
  }

  private def getNextPlayerName(state: TrucoManagerState): String = {
    state.trucoMatch.currentPlayer match {
      case state.trucoMatch.firstPlayer  => state.firstPlayer.playerName
      case state.trucoMatch.secondPlayer => state.secondPlayer.playerName
    }
  }
}
