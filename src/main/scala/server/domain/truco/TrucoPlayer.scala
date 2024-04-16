package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum

class TrucoPlayer(var hand: Hand) {
  var points = 0
  var shout =
    None: Option[
      TrucoEnum | EnvidoEnum
    ] // None if no shout was made in the last turn

  def replaceHand(newHand: Hand): Unit = {
    hand = newHand
  }

  def play(cardIdx: Int): Card = {
    hand.playCardAt(cardIdx)
  }
}
