package server.domain.structs.truco

import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.Player
import server.domain.truco.TrucoMatch

final case class TrucoManagerPlayerState(
    player: EntityRef[Player.Command],
    playerName: String,
    hasInit: Boolean
)

final case class TrucoManagerState(
    firstPlayer: TrucoManagerPlayerState,
    secondPlayer: TrucoManagerPlayerState,
    trucoMatch: TrucoMatch
)
