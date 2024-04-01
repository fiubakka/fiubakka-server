package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck

class Hand(deck: Deck) {
  val InitialCardAmount = 3
  var cards: List[Card] = deck.take(InitialCardAmount)

  def playCardAt(idx: Int): Card = {
    val card = cards(idx)
    cards = cards.patch(idx, Nil, 1)
    card
  }
}
