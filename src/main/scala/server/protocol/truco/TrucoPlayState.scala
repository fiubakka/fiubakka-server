package server.protocol.truco

import server.domain.structs.truco.TrucoShoutEnum.TrucoShoutEnum
import server.domain.truco.cards.Card

final case class TrucoPlayState(
    playId: Int,
    playType: TrucoPlayType.TrucoPlayType,
    firstPlayerPoints: TrucoPoints,
    secondPlayerPoints: TrucoPoints,
    playerCards: Seq[TrucoCard],
    opponentCardAmount: Int,
    isGameOver: Boolean,
    isMatchOver: Boolean,
    card: Option[TrucoCard] = None,
    shout: Option[TrucoShoutEnum] = None,
    nextPlayInfo: Option[TrucoNextPlayInfo] = None
)

final case class TrucoCard(
    cardId: Int,
    card: Card
) {
  def numberToInt(): Int = card.number match {
    case server.domain.truco.cards.CardNumber.Ace    => 1
    case server.domain.truco.cards.CardNumber.Two    => 2
    case server.domain.truco.cards.CardNumber.Three  => 3
    case server.domain.truco.cards.CardNumber.Four   => 4
    case server.domain.truco.cards.CardNumber.Five   => 5
    case server.domain.truco.cards.CardNumber.Six    => 6
    case server.domain.truco.cards.CardNumber.Seven  => 7
    case server.domain.truco.cards.CardNumber.Ten    => 10
    case server.domain.truco.cards.CardNumber.Eleven => 11
    case server.domain.truco.cards.CardNumber.Twelve => 12
  }
}

// Jackson Serialization **does not support** out of the box serialization
// for Scala 3 enums, so we need to use a Scala 2-style Enumeration
object TrucoPlayType extends Enumeration {
  type TrucoPlayType = Value
  val Card, Shout, Update = Value
}

final case class TrucoNextPlayInfo(
    nextPlayer: String,
    isPlayCardAvailable: Boolean,
    availableShouts: Seq[TrucoShoutEnum]
)

final case class TrucoPoints(
    playerName: String,
    points: Int
)
