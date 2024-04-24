package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum
import server.domain.truco.state.CardsRound

object TrucoMatch {
  val LastRound = 2
  val EnvidoPoints = 2
  val RealEnvidoPoints = 3
  val TrucoPoints = 2
  val RetrucoPoints = 1
  val ValecuatroPoints = 1
  val MaxPoints = 30
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
  var areShouting =
    false // True if we are in the shouting stage, false otherwise
  var round =
    0 // 0 to 2, corresponds to the at most 3 cards each player can play
  var trucoPoints = 1 // Base points for each game
  var envidoPoints = 0 // Base points for each Envido game
  var canPlayEnvido = true
  var shouts = List.empty: List[EnvidoEnum | TrucoEnum]

  def lastPlay: Option[Card | EnvidoEnum | TrucoEnum] = {
    currentPlayer match {
      case `firstPlayer`  => secondPlayer.lastAction
      case `secondPlayer` => secondPlayer.lastAction
    }
  }

  def play(cardIdx: Int): Unit = {
    if isGameOver then
      throw new IllegalStateException(
        "Cannot play card, waiting for next game start."
      )
    if areShouting then
      throw new IllegalStateException(
        "Cannot play card, waiting for shouting to end."
      )
    val cardPlayed = currentPlayer.play(cardIdx)
    val currentRoundCards = getCurrentCardsPlayed()
    currentPlayer match {
      case `firstPlayer` => currentRoundCards.firstPlayerCard = Some(cardPlayed)
      case `secondPlayer` =>
        currentRoundCards.secondPlayerCard = Some(cardPlayed)
    }
    resetLastPlayerAction()
    currentPlayer = getNextPlayer()
    currentRoundCards match {
      case CardsRound(Some(_), Some(_))
          if round == TrucoMatch.LastRound => // Do nothing, next game must be explicitly started
      case CardsRound(Some(f), Some(s)) => startNextRound(f, s)
      case _                            => // Do nothing
    }
  }

  // "Cantar" is a Truco term that means to challenge the opponent to play a higher card.
  def shout(shout: EnvidoEnum | TrucoEnum): Unit = {
    if isGameOver then
      throw new IllegalStateException(
        "Cannot shout, waiting for next game start."
      )
    shout match {
      case e: TrucoEnum =>
        if shouts.isEmpty || shouts.last.isInstanceOf[TrucoEnum] then
          trucoShout(e)
        else
          throw new IllegalArgumentException(
            "Cannot shout Truco when shouting Envido"
          )
      case e: EnvidoEnum =>
        if canPlayEnvido then envidoShout(e)
        else throw new IllegalArgumentException("Cannot shout Envido now")
    }
    currentPlayer.shout(shout)
    resetLastPlayerAction()
    currentPlayer = getNextPlayer()
  }

  def startNextGame(): Unit = {
    updateWinnerPoints()
    round = 0
    canPlayEnvido = true
    trucoPoints = 1
    envidoPoints = 0
    areShouting = false
    cardsPlayed = List.empty
    startGamePlayer = getNextStartGamePlayer()
    currentPlayer = startGamePlayer
    val deck = new Deck()
    firstPlayer.replaceHand(new Hand(deck))
    secondPlayer.replaceHand(new Hand(deck))
  }

  def isPlayingCardLegalMove: Boolean = {
    !areShouting && !isGameOver
  }

  def isGameOver: Boolean = {
    gameWinner.isDefined
  }

  def isMatchOver: Boolean = {
    firstPlayer.points == TrucoMatch.MaxPoints ||
    secondPlayer.points == TrucoMatch.MaxPoints
  }

  private def trucoShout(shout: TrucoEnum): Unit = {
    shout match {
      case TrucoEnum.Truco if shouts.isEmpty =>
        areShouting = true
        shouts = shouts :+ shout
      case TrucoEnum.Retruco
          if !shouts.isEmpty && shouts.last == TrucoEnum.Truco =>
        shouts = shouts :+ shout
      case TrucoEnum.Valecuatro
          if !shouts.isEmpty && shouts.last == TrucoEnum.Retruco =>
        shouts = shouts :+ shout
      case _
          if !shouts.isEmpty && !List(TrucoEnum.Quiero, TrucoEnum.NoQuiero)
            .contains(shouts.last) => // Quiero, NoQuiero
        areShouting = false
        if shout == TrucoEnum.NoQuiero then shouts = shouts.dropRight(1)
        trucoPoints = calculateShoutPoints(shouts)
        shouts = List.empty
      case _ => throw new IllegalArgumentException("Invalid Truco shout")
    }
    canPlayEnvido = false
  }

  private def envidoShout(shout: EnvidoEnum): Unit = {
    shout match {
      case EnvidoEnum.Envido
          if shouts.isEmpty || shouts.last == EnvidoEnum.Envido =>
        areShouting = true
        shouts = shouts :+ shout
      case EnvidoEnum.RealEnvido
          if shouts.isEmpty || shouts.last == EnvidoEnum.Envido =>
        areShouting = true
        shouts = shouts :+ shout
      case EnvidoEnum.FaltaEnvido
          if shouts.isEmpty || !List(EnvidoEnum.Quiero, EnvidoEnum.NoQuiero)
            .contains(shouts.last) =>
        areShouting = true
        shouts = shouts :+ shout
      case _
          if !shouts.isEmpty && !List(EnvidoEnum.Quiero, EnvidoEnum.NoQuiero)
            .contains(shouts.last) => // Quiero, NoQuiero
        areShouting = false
        if shout == EnvidoEnum.NoQuiero then shouts = shouts.dropRight(1)
        envidoPoints = calculateShoutPoints(shouts)
        if envidoPoints == 0 then
          envidoPoints += 1 // Base points for Envido denied
        shouts = List.empty
        canPlayEnvido = false
      case _ => throw new IllegalArgumentException("Invalid Envido shout")
    }
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

  private def gameWinner: Option[TrucoPlayer] = {
    // Game ends when the 3 rounds are played or when one of the players
    // wins 2 rounds. It can also end if one of the players deny a Truco shout.
    // Positive netScore means the first player won the game (if 3 rounds played).
    // Negative netScore means the second player won the game (if 3 rounds played).
    // Zero netScore means the first player to play the a card in the game
    // won, known as "mano" in Truco game rules (if 3 rounds played).
    // If the score is +-2, the game ends regardless of the rounds played (so it could end in the second round).

    val maybePlayerDeniedTruco =
      if firstPlayer.shout == Some(TrucoEnum.NoQuiero) then Some(firstPlayer)
      else if secondPlayer.shout == Some(TrucoEnum.NoQuiero) then
        Some(secondPlayer)
      else None

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

    maybePlayerDeniedTruco match {
      case Some(player) => Some(player)
      case None =>
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
  }

  private def calculateShoutPoints(
      shouts: List[EnvidoEnum | TrucoEnum]
  ): Int = {
    // In practice the List should only contain a single type of Shout because of rules checking when shouting
    shouts.foldLeft(0) { (acc, shout) =>
      shout match {
        case EnvidoEnum.Envido     => acc + TrucoMatch.EnvidoPoints
        case EnvidoEnum.RealEnvido => acc + TrucoMatch.RealEnvidoPoints
        case EnvidoEnum.FaltaEnvido =>
          Math.min(
            TrucoMatch.MaxPoints - firstPlayer.points,
            TrucoMatch.MaxPoints - secondPlayer.points
          )
        case TrucoEnum.Truco      => acc + TrucoMatch.TrucoPoints
        case TrucoEnum.Retruco    => acc + TrucoMatch.RetrucoPoints
        case TrucoEnum.Valecuatro => acc + TrucoMatch.ValecuatroPoints
        case _ =>
          acc // Quiero, NoQuiero should not be received anyway, as NoQuiero deletes the last shout in the list and Quiero confirms it
      }
    }
  }

  private def updateWinnerPoints(): Unit = {
    gameWinner match {
      case Some(winner) => winner.points += trucoPoints
      case None         => // Should never happen
    }
  }

  private def resetLastPlayerAction(): Unit = {
    currentPlayer match {
      case `firstPlayer`  => secondPlayer.resetLastAction()
      case `secondPlayer` => firstPlayer.resetLastAction()
    }
  }
}
