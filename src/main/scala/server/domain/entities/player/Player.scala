package server.domain.entities.player

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.util.Timeout
import server.domain.entities.player.behavior.PlayerInitBehavior
import server.domain.entities.player.command.*
import server.domain.entities.truco.TrucoManager
import server.domain.structs.DurablePlayerState
import server.domain.structs.inventory.Equipment
import server.infra.PlayerPersistor
import server.sharding.Sharding

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object Player {
  export PlayerActionCommand.*
  export PlayerEventCommand.*
  export PlayerReplyCommand.*

  final case class InitData(
      handler: ActorRef[PlayerReplyCommand.ReplyCommand],
      equipment: Option[Equipment]
  )

  type Command = ActionCommand | TrucoManager.ReplyCommand

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val askTimeout = Timeout(30, TimeUnit.SECONDS)

        timers.startTimerWithFixedDelay(
          "persist",
          PersistState(),
          30.seconds
        )

        timers.startTimerWithFixedDelay(
          "checkHeartbeat",
          CheckHeartbeat(),
          5.seconds
        ) // It will only start actually checking after it goes to the running state

        ctx.log.info(s"Starting player $entityId")

        val persistor = Sharding().entityRefFor(
          PlayerPersistor.TypeKey,
          entityId
        )

        ctx.ask(
          persistor,
          PlayerPersistor.GetState.apply
        ) {
          case Success(PlayerPersistor.GetStateResponse(initialState)) => {
            InitialState(initialState)
          }
          case Failure(ex) => {
            ctx.log.error(s"Failed to get player state: $ex")
            throw ex
          }
        }

        PlayerInitBehavior(entityId, persistor)
      }
    }
  }
}
