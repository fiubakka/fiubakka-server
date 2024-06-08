package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.Mazo
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
  var previousPlayer = firstPlayer
  var startGamePlayer = firstPlayer // Player that started the current game
  var startedShoutingPlayer: Option[TrucoPlayer] =
    None // Player that started the shouting phase
  var playerWithTrucoQuiero =
    None: Option[
      TrucoPlayer
    ] // Player that shouted Quiero in Truco, useful for keeping track of available shouts

  var cardsPlayed: List[CardsRound] = List.empty
  var areShouting =
    false // True if we are in the shouting stage, false otherwise
  var round =
    0 // 0 to 2, corresponds to the at most 3 cards each player can play
  var trucoPoints = 1 // Base points for each game
  var envidoPoints = 0 // Base points for each Envido game
  var canPlayEnvido = true
  var shouts = List.empty: List[EnvidoEnum | TrucoEnum]

  def lastPlay: Option[Card | EnvidoEnum | TrucoEnum | Mazo] = {
    // Only one player at a time should have a lastAction defined
    if firstPlayer.lastAction.isDefined then firstPlayer.lastAction
    else if secondPlayer.lastAction.isDefined then secondPlayer.lastAction
    else None
  }

  def goToMazo(): Unit = {
    if isGameOver then
      throw new IllegalStateException(
        "Cannot go to Mazo, waiting for next game start."
      )
    if areShouting then
      throw new IllegalStateException(
        "Cannot go to Mazo, waiting for shouting to end."
      )
    if !isMazoAvailable then
      throw new IllegalStateException("Cannot go to Mazo now.")
    resetLastPlayerAction()
    currentPlayer.goToMazo()
    if round == 0 && currentPlayer == startGamePlayer then
      trucoPoints =
        2 // If the first player goes to Mazo, the game is worth 2 points because it skipped envido
    previousPlayer = currentPlayer
    currentPlayer = getNextPlayer()
  }

  def play(cardId: Int): Unit = {
    if isGameOver then
      throw new IllegalStateException(
        "Cannot play card, waiting for next game start."
      )
    if areShouting then
      throw new IllegalStateException(
        "Cannot play card, waiting for shouting to end."
      )
    if !currentPlayer.isValidCard(cardId) then
      throw new IllegalArgumentException("Cannot play card, invalid card id.")

    resetLastPlayerAction()

    val cardPlayed = currentPlayer.play(cardId)
    val currentRoundCards = getCurrentCardsPlayed()
    currentPlayer match {
      case `firstPlayer` => currentRoundCards.firstPlayerCard = Some(cardPlayed)
      case `secondPlayer` =>
        currentRoundCards.secondPlayerCard = Some(cardPlayed)
    }
    previousPlayer = currentPlayer
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

    val wereShouting = areShouting

    shout match {
      case e: TrucoEnum =>
        if shouts.isEmpty || shouts.last.isInstanceOf[TrucoEnum] then
          trucoShout(e)
          resetLastPlayerAction()
          currentPlayer.shout(shout)
        else
          throw new IllegalArgumentException(
            "Cannot shout Truco when shouting Envido"
          )
      case e: EnvidoEnum =>
        if canPlayEnvido then {
          // If the last shout was Truco, we reset the shouts
          if !shouts.isEmpty && shouts.last == TrucoEnum.Truco then
            shouts = List.empty
          envidoShout(e)
          resetLastPlayerAction()
          currentPlayer.shout(shout)
          if envidoWinner.isDefined then {
            val winner = envidoWinner.get
            winner.points += envidoPoints
          }
        } else throw new IllegalArgumentException("Cannot shout Envido now")
    }

    previousPlayer = currentPlayer

    // Store the player that initiated the shouting, needed for determining
    // which player should play after shouting is over
    if !wereShouting && areShouting then
      startedShoutingPlayer = Some(currentPlayer)
      currentPlayer = getNextPlayer()
    else if wereShouting && !areShouting then
      currentPlayer = getNextPlayer()
      startedShoutingPlayer = None // Reset after shouting is over
    else currentPlayer = getNextPlayer()
  }

  def startNextGame(): Unit = {
    updateWinnerPoints()
    round = 0
    canPlayEnvido = true
    trucoPoints = 1
    envidoPoints = 0
    shouts = List.empty
    areShouting = false
    cardsPlayed = List.empty
    startGamePlayer = getNextStartGamePlayer()
    startedShoutingPlayer = None
    currentPlayer = startGamePlayer
    previousPlayer = startGamePlayer
    playerWithTrucoQuiero = None
    firstPlayer.resetLastAction()
    secondPlayer.resetLastAction()
    val deck = new Deck()
    firstPlayer.replaceHand(new Hand(deck))
    secondPlayer.replaceHand(new Hand(deck))
  }

  // We don't consider "Mazo" as a Shout per se here
  def availableShouts: List[EnvidoEnum | TrucoEnum] = {
    availableEnvidoShouts ++ availableTrucoShouts
  }

  def isMazoAvailable: Boolean = {
    !isGameOver && !areShouting
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

  private def availableEnvidoShouts: List[EnvidoEnum] = {
    if !canPlayEnvido then List.empty
    else if shouts.isEmpty || shouts.last == TrucoEnum.Truco then {
      List(EnvidoEnum.Envido, EnvidoEnum.RealEnvido, EnvidoEnum.FaltaEnvido)
    } else {
      shouts.last match { // All shouts must be of the same type
        case EnvidoEnum.Envido =>
          (if (shouts.length == 1)
           then // Envido is available also if the first shout was Envido, but only once more
             List(EnvidoEnum.Envido)
           else List.empty) ++
            List(
              EnvidoEnum.RealEnvido,
              EnvidoEnum.FaltaEnvido,
              EnvidoEnum.Quiero,
              EnvidoEnum.NoQuiero
            )
        case EnvidoEnum.RealEnvido =>
          List(EnvidoEnum.FaltaEnvido, EnvidoEnum.Quiero, EnvidoEnum.NoQuiero)
        case EnvidoEnum.FaltaEnvido =>
          List(EnvidoEnum.Quiero, EnvidoEnum.NoQuiero)
        case _ =>
          List.empty // Envido shouting is over, so there are no Envido options to shout
      }
    }
  }

  private def availableTrucoShouts: List[TrucoEnum] = {
    if shouts.isEmpty then {
      List(TrucoEnum.Truco) // No shouts yet, so Truco is an available option
    } else {
      shouts.last match { // All shouts must be of the same type
        case TrucoEnum.Truco =>
          List(TrucoEnum.Retruco, TrucoEnum.Quiero, TrucoEnum.NoQuiero)
        case TrucoEnum.Retruco =>
          List(TrucoEnum.Valecuatro, TrucoEnum.Quiero, TrucoEnum.NoQuiero)
        case TrucoEnum.Valecuatro => List(TrucoEnum.Quiero, TrucoEnum.NoQuiero)
        case TrucoEnum.Quiero
            if (playerWithTrucoQuiero.isDefined && currentPlayer == playerWithTrucoQuiero.get) =>
          shouts.dropRight(1).last match {
            case TrucoEnum.Truco   => List(TrucoEnum.Retruco)
            case TrucoEnum.Retruco => List(TrucoEnum.Valecuatro)
            case _                 => List.empty
          }
        case _ =>
          List.empty // Truco shouting is over, so there are no Truco options to shout
      }
    }
  }

  private def trucoShout(shout: TrucoEnum): Unit = {
    shout match {
      case TrucoEnum.Truco if shouts.isEmpty =>
        areShouting = true
        shouts = shouts :+ shout
      case TrucoEnum.Retruco
          if !shouts.isEmpty && shouts.last == TrucoEnum.Truco =>
        shouts = shouts :+ shout
      case TrucoEnum.Retruco
          if !shouts.isEmpty && shouts.last == TrucoEnum.Quiero =>
        if shouts.dropRight(1).last != TrucoEnum.Truco then
          throw new IllegalArgumentException(
            "Cannot shout Retruco when last accepted shout was not Truco!"
          )
        areShouting = true
        shouts =
          shouts.dropRight(1) :+ shout // Replace Quiero with the new shout
      case TrucoEnum.Valecuatro
          if !shouts.isEmpty && shouts.last == TrucoEnum.Retruco =>
        shouts = shouts :+ shout
      case TrucoEnum.Valecuatro
          if !shouts.isEmpty && shouts.last == TrucoEnum.Quiero =>
        if shouts.dropRight(1).last != TrucoEnum.Retruco then
          throw new IllegalArgumentException(
            "Cannot shout Valecuatro when last accepted shout was not Retruco!"
          )
        areShouting = true
        shouts =
          shouts.dropRight(1) :+ shout // Replace Quiero with the new shout
      case _
          if !shouts.isEmpty && !List(TrucoEnum.Quiero, TrucoEnum.NoQuiero)
            .contains(shouts.last) => // Quiero, NoQuiero
        areShouting = false
        if shout == TrucoEnum.NoQuiero then shouts = shouts.dropRight(1)
        else if shout == TrucoEnum.Quiero then {
          playerWithTrucoQuiero = Some(currentPlayer)
          shouts = shouts :+ shout
        }
        trucoPoints = calculateShoutPoints()
      case _ => throw new IllegalArgumentException("Invalid Truco shout")
    }
    if round == 0 && currentPlayer != startGamePlayer
    then // In the first round, a Truco shout can be countershouted by Envido
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
          if shouts.isEmpty || !List(
            EnvidoEnum.Quiero,
            EnvidoEnum.NoQuiero,
            EnvidoEnum.FaltaEnvido
          )
            .contains(shouts.last) =>
        areShouting = true
        shouts = shouts :+ shout
      case EnvidoEnum.Quiero | EnvidoEnum.NoQuiero
          if !shouts.isEmpty && !List(EnvidoEnum.Quiero, EnvidoEnum.NoQuiero)
            .contains(shouts.last) =>
        areShouting = false
        if shout == EnvidoEnum.NoQuiero then shouts = shouts.dropRight(1)
        else if shout == EnvidoEnum.Quiero then shouts = shouts :+ shout
        envidoPoints = calculateShoutPoints()
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
    if round >= 1 then
      canPlayEnvido = false // Can't play envido after the first round
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
    if !areShouting && startedShoutingPlayer.isDefined then
      startedShoutingPlayer.get
    else if currentPlayer == firstPlayer then secondPlayer
    else firstPlayer
  }

  private def getNextStartGamePlayer(): TrucoPlayer = {
    if startGamePlayer == firstPlayer then secondPlayer
    else firstPlayer
  }

  private def envidoWinner: Option[TrucoPlayer] = {
    val maybePlayerDeniedEnvido =
      if firstPlayer.shout == Some(EnvidoEnum.NoQuiero) then Some(secondPlayer)
      else if secondPlayer.shout == Some(EnvidoEnum.NoQuiero) then
        Some(firstPlayer)
      else None

    maybePlayerDeniedEnvido match {
      case Some(player) => Some(player)
      case None =>
        val firstPlayerScore = firstPlayer.calculateEnvidoScore()
        val secondPlayerScore = secondPlayer.calculateEnvidoScore()
        val envidoWinner =
          if firstPlayerScore >= secondPlayerScore then
            firstPlayer // TODO take into account the mano
          else secondPlayer
        Some(envidoWinner)
    }
  }

  private def gameWinner: Option[TrucoPlayer] = {
    // Game ends when the 3 rounds are played or when one of the players
    // wins 2 rounds. It can also end if one of the players deny a Truco shout.
    // Positive netScore means the first player won the game (if 3 rounds played).
    // Negative netScore means the second player won the game (if 3 rounds played).
    // Zero netScore means the first player to play the a card in the game
    // won, known as "mano" in Truco game rules (if 3 rounds played).
    // If the score is +-2, the game ends regardless of the rounds played (so it could end in the second round).

    val maybePlayerWentToMazo =
      if firstPlayer.wentToMazo then Some(secondPlayer)
      else if secondPlayer.wentToMazo then Some(firstPlayer)
      else None

    val maybePlayerDeniedTruco =
      if firstPlayer.shout == Some(TrucoEnum.NoQuiero) then Some(secondPlayer)
      else if secondPlayer.shout == Some(TrucoEnum.NoQuiero) then
        Some(firstPlayer)
      else None

    val maybePlayerWonByMazoOrTrucoDenied =
      maybePlayerWentToMazo.orElse(maybePlayerDeniedTruco)

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

    maybePlayerWonByMazoOrTrucoDenied match {
      case Some(player) => Some(player)
      case None =>
        round match {
          case TrucoMatch.LastRound
              if currentCardsPlayed.firstPlayerCard.isDefined && currentCardsPlayed.secondPlayerCard.isDefined => { // End of Last Round
            val firstRoundCardsPlayed = cardsPlayed(
              0
            ) // It should always be defined

            if netScore > 0 then Some(firstPlayer)
            else if netScore < 0 then Some(secondPlayer)
            else if firstRoundCardsPlayed.firstPlayerCard.get > firstRoundCardsPlayed.secondPlayerCard.get
            then
              Some(
                firstPlayer
              ) // If last round was a draw, the player that won the first round wins
            else if firstRoundCardsPlayed.firstPlayerCard.get < firstRoundCardsPlayed.secondPlayerCard.get
            then
              Some(
                secondPlayer
              )
            else if startGamePlayer == firstPlayer then
              Some(firstPlayer) // If all cards were equal, the Mano wins
            else Some(secondPlayer)
          }

          case TrucoMatch.LastRound => { // Beginning of Last Round
            if netScore > 0 then Some(firstPlayer)
            else if netScore < 0 then Some(secondPlayer)
            else None
          }

          case _ => None
        }
    }
  }

  private def calculateShoutPoints(): Int = {
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
          acc
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
    previousPlayer match {
      case `firstPlayer`  => firstPlayer.resetLastAction()
      case `secondPlayer` => secondPlayer.resetLastAction()
    }
  }
}
