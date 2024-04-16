package server.domain.truco.cards

import scala.util.Random

class Deck {
  var cards: List[Card] = {
    val numbers = CardNumber.values.toList
    val suits = CardSuit.values.toList
    val random = new Random()
    random.shuffle(
      for
        number <- numbers
        suit <- suits
      yield new Card(number, suit)
    )
  }

  def take(n: Int): List[Card] = {
    val (taken, remaining) = cards.splitAt(n)
    cards = remaining
    taken
  }
}
