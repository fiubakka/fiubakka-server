package server.domain.truco.cards

object Card {
  private val CardOrder
      : List[(CardNumber.CardNumber, Option[CardSuit.CardSuit])] = List(
    (CardNumber.Ace, Some(CardSuit.Swords)),
    (CardNumber.Ace, Some(CardSuit.Clubs)),
    (CardNumber.Seven, Some(CardSuit.Swords)),
    (CardNumber.Seven, Some(CardSuit.Coins)),
    (CardNumber.Three, None),
    (CardNumber.Two, None),
    (CardNumber.Ace, None),
    (CardNumber.Twelve, None),
    (CardNumber.Eleven, None),
    (CardNumber.Ten, None),
    (CardNumber.Seven, None),
    (CardNumber.Six, None),
    (CardNumber.Five, None),
    (CardNumber.Four, None)
  )

  private def getCardOrder(card: Card): Int = {
    CardOrder.indexWhere { case (number, suit) =>
      card.number == number && (suit.isEmpty || card.suit == suit.get)
    }
  }
}

class Card(val number: CardNumber.CardNumber, val suit: CardSuit.CardSuit) {
  override def toString: String = s"${number} of ${suit}"

  def >(other: Card): Boolean = {
    Card.getCardOrder(this) < Card.getCardOrder(other)
  }

  def >=(other: Card): Boolean = {
    Card.getCardOrder(this) <= Card.getCardOrder(other)
  }

  def <(other: Card): Boolean = {
    Card.getCardOrder(this) > Card.getCardOrder(other)
  }

  def <=(other: Card): Boolean = {
    Card.getCardOrder(this) >= Card.getCardOrder(other)
  }

  def ==(other: Card): Boolean = {
    Card.getCardOrder(this) == Card.getCardOrder(other)
  }
}
