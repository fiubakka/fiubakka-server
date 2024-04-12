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
}
