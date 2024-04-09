package server.domain.truco

import server.domain.truco.Hand
import server.domain.truco.cards.Card

class TrucoPlayer(var hand: Hand) {
  var points = 0

  def replaceHand(newHand: Hand): Unit = {
    hand = newHand
  }

  def play(cardIdx: Int): Card = {
    hand.playCardAt(cardIdx)
  }
}
