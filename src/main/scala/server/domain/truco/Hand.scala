package server.domain.truco

import server.domain.truco.cards.Card
import server.domain.truco.cards.Deck

object Hand {
  val InitialCardAmount = 3
  val MinCardCountForEnvido = 2
  val BaseEnvidoScore = 20
}

class Hand(deck: Deck) {

  /** List of cards with a boolean indicating if the card has been played */
  var cards: List[(Card, Boolean)] =
    deck.take(Hand.InitialCardAmount).map { c => (c, false) }

  def playCardAt(idx: Int): Card = {
    val card = cards(idx)
    if card._2 then throw new Exception("Card has already been played")
    val cardValue = card._1
    cards = cards.patch(idx, List((cardValue, true)), 1)
    cardValue
  }

  def calculateEnvidoScore(): Int = {
    implicit val cardOrderingX = new Ordering[Card] {
      def compare(a: Card, b: Card): Int = {
        a.number.envidoValue - b.number.envidoValue
      }
    }

    val groupedBySuit = cards.map(_._1).groupBy(_.suit)
    // Mathematically there can only be 1 group with 2 or more cards. If none is found then use the max card in the Hand.
    // We sort them to get the 2 highest cards in the group if there are more than 1 card, else we are getting the only card (the max card).
    val envidoCards = groupedBySuit
      .find(_._2.size >= Hand.MinCardCountForEnvido)
      .map(_._2)
      .getOrElse(List(cards.map(_._1).max))
      .sorted
      .takeRight(2)

    val envidoScore =
      envidoCards.map(_.number.envidoValue).reduce((a, b) => a + b)

    if envidoCards.length == 2 then envidoScore + Hand.BaseEnvidoScore
    else envidoScore
  }
}
