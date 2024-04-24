package server.protocol.truco

import server.domain.structs.truco.TrucoShoutEnum
import server.domain.truco.cards.Card

final case class TrucoPlayState(
    playId: Int,
    playType: TrucoPlayType,
    firstPlayerPoints: TrucoPoints,
    secondPlayerPoints: TrucoPoints,
    playerCards: Seq[TrucoCard],
    opponentCardAmount: Int,
    isGameOver: Boolean,
    isMatchOver: Boolean,
    card: Option[Card] = None,
    shout: Option[TrucoShoutEnum] = None,
    nextPlayInfo: Option[TrucoNextPlayInfo] = None
)

final case class TrucoCard(
    cardId: Int,
    card: Card
)

enum TrucoPlayType {
  case Card, Shout
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
