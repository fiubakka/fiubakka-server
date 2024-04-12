package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck
import server.domain.truco.state.CardsRound

object TrucoMatch {
  val LastRound = 2
}

// A Match of Truco is composed by many games.
// Each game consists of at most 3 cards played by each player.
// Each of the 3 cards play is called a round.
// The game ends when one of the players reaches 30 points, regardless of the number of games played.
class TrucoMatch {
  val deck = new Deck()

  val firstPlayer =
    new TrucoPlayer(new Hand(deck)) // Player that starts the match
  val secondPlayer = new TrucoPlayer(new Hand(deck))
  var currentPlayer = firstPlayer // Player with the current turn
  var startGamePlayer = firstPlayer // Player that started the current game

  var cardsPlayed: List[CardsRound] = List.empty
  var round =
    0 // 0 to 2, corresponds to the at most 3 cards each player can play

  def play(cardIdx: Int): Unit = {
    val cardPlayed = currentPlayer.play(cardIdx)
    val currentRoundCards = getCurrentCardsPlayed()
    currentPlayer match {
      case `firstPlayer` => currentRoundCards.firstPlayerCard = Some(cardPlayed)
      case `secondPlayer` =>
        currentRoundCards.secondPlayerCard = Some(cardPlayed)
    }
    currentPlayer = getNextPlayer()
    currentRoundCards match {
      case CardsRound(Some(_), Some(_))
          if round == TrucoMatch.LastRound => // Do nothing, next game must be explicitly started
      case CardsRound(Some(f), Some(s)) => startNextRound(f, s)
      case _                            => // Do nothing
    }
  }

  def startNextGame(): Unit = {
    updateWinnerPoints()
    round = 0
    cardsPlayed = List.empty
    startGamePlayer = getNextStartGamePlayer()
    currentPlayer = startGamePlayer
    val deck = new Deck()
    firstPlayer.replaceHand(new Hand(deck))
    secondPlayer.replaceHand(new Hand(deck))
  }

  private def startNextRound(
      firstPlayerCard: Card,
      secondPlayerCard: Card
  ): Unit = {
    round += 1
    if firstPlayerCard > secondPlayerCard then currentPlayer = firstPlayer
    else if firstPlayerCard < secondPlayerCard then currentPlayer = secondPlayer
    // If the cards are equal, the currentPlayer remains the same
  }

  private def getCurrentCardsPlayed(): CardsRound = {
    val currentCardsPlayed = cardsPlayed.lift(round)
    currentCardsPlayed match {
      case Some(cardsRound) => cardsRound
      case None =>
        val newCardsRound = CardsRound(None, None)
        cardsPlayed = cardsPlayed :+ newCardsRound
        newCardsRound
    }
  }

  private def getNextPlayer(): TrucoPlayer = {
    if currentPlayer == firstPlayer then secondPlayer
    else firstPlayer
  }

  private def getNextStartGamePlayer(): TrucoPlayer = {
    if startGamePlayer == firstPlayer then secondPlayer
    else firstPlayer
  }

  private def getGameWinner(): Option[TrucoPlayer] = {
    // Game ends when the 3 rounds are played or when one of the players
    // wins 2 rounds.
    // Positive netScore means the first player won the game (if 3 rounds played).
    // Negative netScore means the second player won the game (if 3 rounds played).
    // Zero netScore means the first player to play the a card in the game
    // won, known as "mano" in Truco game rules (if 3 rounds played).
    // If the score is +-2, the game ends regardless of the rounds played (so it could end in the second round).

    val netScore = cardsPlayed.foldLeft(0) { (acc, cardsRound) =>
      cardsRound match {
        case CardsRound(Some(firstPlayerCard), Some(secondPlayerCard)) =>
          if firstPlayerCard > secondPlayerCard then acc + 1
          else if firstPlayerCard < secondPlayerCard then acc - 1
          else acc
        case _ => acc
      }
    }

    val currentCardsPlayed = getCurrentCardsPlayed()

    round match {
      case TrucoMatch.LastRound
          if currentCardsPlayed.firstPlayerCard.isDefined && currentCardsPlayed.secondPlayerCard.isDefined =>
        if netScore > 0 then Some(firstPlayer)
        else if netScore < 0 then Some(secondPlayer)
        else if startGamePlayer == firstPlayer then Some(firstPlayer)
        else Some(secondPlayer)
      case _ =>
        if netScore == 2 then Some(firstPlayer)
        else if netScore == -2 then Some(secondPlayer)
        else None
    }
  }

  private def updateWinnerPoints(): Unit = {
    val maybeWinner = getGameWinner()
    maybeWinner match {
      case Some(winner) => winner.points += 1
      case None         => // Should never happen
    }
  }
}
