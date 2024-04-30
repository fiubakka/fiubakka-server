package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck

object Hand {
  val InitialCardAmount = 3
}

class Hand(deck: Deck) {
  var cards: List[Option[Card]] =
    deck.take(Hand.InitialCardAmount).map { c => Some(c) }

  def playCardAt(idx: Int): Card = {
    val card = cards(idx)
    cards = cards.patch(idx, List(None), 1)
    card.get
  }
}
