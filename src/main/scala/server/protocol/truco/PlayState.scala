package server.protocol.truco

import server.domain.structs.truco.TrucoShoutEnum
import server.domain.truco.cards.Card

final case class PlayState(
    playId: Int,
    playType: PlayType,
    firstPlayerPoints: TrucoPoints,
    secondPlayerPoints: TrucoPoints,
    playerCards: Seq[Card],
    opponentCardAmount: Int,
    isGameOver: Boolean,
    isMatchOver: Boolean,
    card: Option[Card] = None,
    shout: Option[TrucoShoutEnum] = None,
    nextPlayInfo: Option[NextPlayInfo] = None
)

enum PlayType {
  case Card, Shout
}

final case class NextPlayInfo(
    nextPlayer: String,
    isPlayCardAvailable: Boolean,
    availableShouts: Seq[TrucoShoutEnum]
)

final case class TrucoPoints(
    playerName: String,
    points: Int
)
