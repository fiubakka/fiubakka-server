package server.domain.truco.cards

class Deck {
  import scala.util.Random

  val cards: List[Card] = {
    val numbers = CardNumber.values.toList
    val suits = CardSuit.values.toList
    val random = new Random()
    for {
      number <- random.shuffle(numbers)
      suit <- random.shuffle(suits)
    } yield new Card(number, suit)
  }
}
