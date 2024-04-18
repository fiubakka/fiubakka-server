package server.domain.entities.player.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerCommand._
import server.domain.entities.player.command.PlayerReplyCommand._
import server.domain.entities.player.utils.PlayerUtils
import server.domain.structs.DurablePlayerState
import server.domain.structs.PlayerState
import server.domain.structs.TransientPlayerState
import server.domain.structs.movement.Velocity
import server.infra.PlayerPersistor

import java.time.LocalDateTime

object PlayerInitBehavior {
  def apply(
      entityId: String,
      persistor: EntityRef[PlayerPersistor.Command],
      initialData: Option[Player.InitData] = None
  ): Behavior[Player.Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case InitialState(initialState) => {
          initialData match {
            case Some(initialData) =>
              finishInitialization(
                ctx,
                persistor,
                initialData,
                initialState
              )

            case None => {
              Behaviors.receiveMessage {
                case Init(initialData) =>
                  finishInitialization(
                    ctx,
                    persistor,
                    initialData,
                    initialState
                  )

                // See below usage of Heartbeat to understand this case
                case Heartbeat(handler) =>
                  finishInitialization(
                    ctx,
                    persistor,
                    Player.InitData(handler, None),
                    initialState
                  ) // No equipment because the Player should already be registered

                // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
                case _ => {
                  Behaviors.same
                }
              }
            }
          }
        }

        // Optimization to avoid waiting for the second Init message to start
        case Init(initData) => {
          apply(entityId, persistor, Some(initData))
        }

        // We know that this message will only be recieved if the PlayerHandler is in
        // the running state, so its safe to assume we won't receive an Init message in this case
        // and instead set this sender as the PlayerHandler and complete intialization.
        // This would be what should happen if the Player dies or is moved to another node.
        case Heartbeat(handler) => {
          apply(
            entityId,
            persistor,
            Some(Player.InitData(handler, None))
          ) // No equipment because the Player should already be registered
        }

        case _ => {
          // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
          Behaviors.same
        }
      }
    }
  }

  private def finishInitialization(
      ctx: ActorContext[Player.Command],
      persistor: EntityRef[PlayerPersistor.Command],
      initialData: Player.InitData,
      initialState: DurablePlayerState
  ): Behavior[Player.Command] = {
    val Player.InitData(handler, equipment) = initialData
    var newState = initialState

    if equipment.isDefined then { // If it's a new player
      // In practice, the persistor entityId is the playername, so set it here
      newState = initialState.copy(
        playerName = persistor.entityId,
        equipment = equipment.get
      )
    }

    val (eventConsumer, eventProducer) =
      PlayerUtils.getEventHandlers(ctx, initialState.mapId)

    handler ! Ready(newState)
    PlayerRunningBehavior(
      PlayerState(
        newState,
        tState = TransientPlayerState(
          handler,
          eventProducer,
          eventConsumer,
          LocalDateTime.now(),
          Velocity(0, 0)
        )
      ),
      persistor
    )
  }
}
