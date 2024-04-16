package server.truco

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.serialization.jackson.CborSerializable
import server.domain.entities.Player
import server.sharding.Sharding

object TrucoManager {
  final case class State(
      firstPlayer: EntityRef[Player.Command],
      secondPlayer: EntityRef[Player.Command]
  )

  sealed trait Command extends CborSerializable

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

      State(
        sharding.entityRefFor(Player.TypeKey, firstPlayerName),
        sharding.entityRefFor(Player.TypeKey, secondPlayerName)
      )

      Behaviors.receiveMessage { case _ =>
        Behaviors.same
      }
    }
  }
}
