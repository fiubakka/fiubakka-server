package server.domain.truco.state

import server.domain.truco.cards.Card

// While is not a good practice to have a mutable case class, I don't care :)
final case class CardsRound(
    var firstPlayerCard: Option[Card],
    var secondPlayerCard: Option[Card]
) {

  // Returns None if the round is not over yet, or the winner card if it is.
  // Returns Some(None) if the round is over but it was a tie.
  def getWinner(): Option[Option[Card]] = {
    (firstPlayerCard, secondPlayerCard) match {
      case (Some(f), Some(s)) if f > s  => Some(Some(f))
      case (Some(f), Some(s)) if f < s  => Some(Some(s))
      case (Some(f), Some(s)) if f == s => Some(None)
      case _                            => None // Round is not over yet
    }
  }
}
