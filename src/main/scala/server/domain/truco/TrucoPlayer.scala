package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum

class TrucoPlayer(var hand: Hand) {
  var points = 0
  var shout =
    None: Option[
      TrucoEnum | EnvidoEnum
    ] // None if no shout was made in their last turn
  private var cardPlayed =
    None: Option[Card] // None if no card was played in their last turn

  def replaceHand(newHand: Hand): Unit = {
    hand = newHand
  }

  def isValidCard(cardId: Int): Boolean = {
    cardId >= 0 && cardId < hand.cards.length
  }

  def play(cardIdx: Int): Card = {
    cardPlayed = Some(hand.playCardAt(cardIdx))
    cardPlayed.get
  }

  def shout(shout: TrucoEnum | EnvidoEnum): Unit = {
    this.shout = Some(shout)
  }

  def lastAction: Option[Card | TrucoEnum | EnvidoEnum] = {
    if (shout.isEmpty && cardPlayed.isEmpty) then {
      None
    } else {
      Some(shout.getOrElse(cardPlayed.get))
    }
  }

  def resetLastAction() = {
    shout = None
    cardPlayed = None
  }
}
