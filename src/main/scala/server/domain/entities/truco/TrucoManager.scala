package server.domain.entities.truco

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.truco.behavior.TrucoManagerInitBehavior
import server.domain.entities.truco.command.TrucoManagerCommand
import server.domain.structs.truco.TrucoManagerPlayerState
import server.domain.structs.truco.TrucoManagerState
import server.domain.truco.TrucoMatch
import server.sharding.Sharding

object TrucoManager {
  export TrucoManagerCommand._

  def apply(
      firstPlayerName: String,
      secondPlayerName: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.log.info(
        "Starting TrucoManager for players {} and {}",
        firstPlayerName,
        secondPlayerName
      )

      val sharding = Sharding()

      val state = TrucoManagerState(
        firstPlayer = TrucoManagerPlayerState(
          sharding.entityRefFor(Player.TypeKey, firstPlayerName),
          playerName = firstPlayerName,
          hasInit = false
        ),
        secondPlayer = TrucoManagerPlayerState(
          sharding.entityRefFor(Player.TypeKey, secondPlayerName),
          playerName = secondPlayerName,
          hasInit = false
        ),
        trucoMatch = new TrucoMatch(),
        playId = 0
      )

      TrucoManagerInitBehavior(state)
    }
  }
}
