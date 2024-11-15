package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.truco.command.TrucoManagerCommand.*
import server.domain.entities.truco.command.TrucoManagerReplyCommand.*
import server.domain.structs.truco.TrucoManagerState
import server.domain.structs.truco.TrucoShoutEnum
import server.domain.truco.cards.Card
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.Mazo
import server.domain.truco.shouts.TrucoEnum
import server.protocol.truco.TrucoCard
import server.protocol.truco.TrucoNextPlayInfo
import server.protocol.truco.TrucoPlayState
import server.protocol.truco.TrucoPlayType
import server.protocol.truco.TrucoPoints

import scala.concurrent.duration.*

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
            val firstPlayerAckPlay =
              if playerName == state.firstPlayer.playerName then true
              else firstPlayerAck
            val secondPlayerAckPlay =
              if playerName == state.secondPlayer.playerName then true
              else secondPlayerAck
            if firstPlayerAckPlay && secondPlayerAckPlay then {
              ctx.log.info("Both players acknowledged play {}", playId)
              timers.cancel("sendMatchState")
              behaviorAfterAck(ctx, state)
            } else
              playerName match {
                case state.firstPlayer.playerName =>
                  behavior(state, firstPlayerAckPlay, secondPlayerAckPlay)
                case state.secondPlayer.playerName =>
                  behavior(state, firstPlayerAck, secondPlayerAckPlay)
              }
          }

          case NotifyPlay() => {
            if !firstPlayerAck then {
              state.firstPlayer.player ! TrucoPlayStateInfo(
                getPlayStateInfoForPlayer(state.firstPlayer.playerName, state)
              )
            }
            if !secondPlayerAck then {
              state.secondPlayer.player ! TrucoPlayStateInfo(
                getPlayStateInfoForPlayer(state.secondPlayer.playerName, state)
              )
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

  private def behaviorAfterAck(
      ctx: ActorContext[Command],
      state: TrucoManagerState
  ): Behavior[Command] = {
    if state.trucoMatch.isMatchOver then {
      ctx.log.info("Match is over! Stopping the TrucoManager")
      Behaviors.stopped
    } else if state.trucoMatch.isGameOver then {
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
  ): TrucoPlayState = {
    val lastPlay = state.trucoMatch.lastPlay
    TrucoPlayState(
      playId = state.playId,
      playType = lastPlay match {
        case None           => TrucoPlayType.Update
        case Some(lp: Card) => TrucoPlayType.Card
        case _              => TrucoPlayType.Shout // Includes Mazo
      },
      playerCards = playerName match {
        case state.firstPlayer.playerName =>
          state.trucoMatch.firstPlayer.hand.cards.zipWithIndex.map {
            case ((card, hasBeenPlayed), idx) =>
              hasBeenPlayed match {
                case false => Some(TrucoCard(idx, card))
                case true  => None
              }
          }.flatten
        case state.secondPlayer.playerName =>
          state.trucoMatch.secondPlayer.hand.cards.zipWithIndex.map {
            case ((card, hasBeenPlayed), idx) =>
              hasBeenPlayed match {
                case false => Some(TrucoCard(idx, card))
                case true  => None
              }
          }.flatten
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
      card = lastPlay match {
        case Some(c: Card) => Some(TrucoCard(-1, c))
        case _             => None
      }, // Card id is not actually used, so fill it with dummy value
      shout = lastPlay match {
        case (Some(shout: (TrucoEnum | EnvidoEnum | Mazo))) =>
          Some(TrucoShoutEnum.fromShoutPlayEnum(shout))
        case _ => None
      },
      nextPlayInfo = Some(
        TrucoNextPlayInfo(
          nextPlayer = getNextPlayerName(state),
          isPlayCardAvailable = state.trucoMatch.isPlayingCardLegalMove,
          availableShouts =
            (if state.trucoMatch.isMazoAvailable then Seq(TrucoShoutEnum.Mazo)
             else Seq.empty) ++ state.trucoMatch.availableShouts.map(
              TrucoShoutEnum.fromShoutPlayEnum
            )
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
