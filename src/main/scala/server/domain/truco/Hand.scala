package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck
import server.domain.truco.cards.CardNumber

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

  def calculateEnvidoScore(): Int = {
    val groupedBySuit = cards.flatten.groupBy(_.suit)
    val envidoGroups = groupedBySuit.filter(_._2.size >= 2)

    val envidoScore = envidoGroups.values.flatten
      .map(_.number match
        case CardNumber.Ace   => 1
        case CardNumber.Two   => 2
        case CardNumber.Three => 3
        case CardNumber.Four  => 4
        case CardNumber.Five  => 5
        case CardNumber.Six   => 6
        case CardNumber.Seven => 7
        case _ => 0
      ).sum

    if envidoGroups.isEmpty then 20 else envidoScore + 20
  }
}
