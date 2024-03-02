package server.domain.truco.cards

import server.domain.truco.cards.CardNumber.CardNumber
import server.domain.truco.cards.CardSuit.CardSuit

class Card(val number: CardNumber, val suit: CardSuit) {
  override def toString: String = s"${number} of ${suit}"
}
