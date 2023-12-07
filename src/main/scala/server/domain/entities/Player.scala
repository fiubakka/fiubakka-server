package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.serialization.jackson.CborSerializable
import akka.util.Timeout
import server.Sharding
import server.domain.structs.DurablePlayerState
import server.domain.structs.GameEntity
import server.domain.structs.GameEntityState
import server.domain.structs.PlayerPosition
import server.domain.structs.PlayerState
import server.domain.structs.TransientPlayerState
import server.infra.PlayerPersistor
import server.protocol.GameEventConsumer
import server.protocol.GameEventProducer
import server.protocol.client.PlayerHandler

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object Player {
  sealed trait Command extends CborSerializable
  final case class Start(playerHandler: ActorRef[PlayerHandler.Command])
      extends Command
  final case class Stop() extends Command
  final case class InitState(
      initialState: DurablePlayerState
  ) extends Command
  final case class Move(
      velX: Float,
      velY: Float,
      replyTo: ActorRef[PlayerHandler.MoveReply]
  ) extends Command
  final case class UpdateEntityState(
      entityId: String,
      newEntityState: GameEntityState
  ) extends Command
  final case class PersistState() extends Command

  Map[String, GameEntity]()

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val askTimeout = Timeout(5.seconds)

        timers.startTimerAtFixedRate(
          "persist",
          PersistState(),
          30.seconds
        )
        ctx.log.info(s"Starting player $entityId")

        val persistor = Sharding().entityRefFor(
          PlayerPersistor.TypeKey,
          entityId
        )

        ctx.spawn(
          GameEventConsumer(entityId, ctx.self),
          s"GameEventConsumer-$entityId"
        )

        val eventProducer = ctx.spawn(
          GameEventProducer(entityId),
          s"GameEventProducer-$entityId"
        )

        ctx.ask(
          persistor,
          PlayerPersistor.GetState.apply
        ) {
          case Success(PlayerPersistor.GetStateResponse(initialState)) => {
            InitState(initialState)
          }
          case Failure(ex) => {
            ctx.log.error(s"Failed to get player state: $ex")
            throw ex
          }
        }

        setupBehaviour(persistor, eventProducer)
      }
    }
  }

  def setupBehaviour(
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: ActorRef[GameEventProducer.Command],
      playerHandler: Option[ActorRef[PlayerHandler.Command]] = None
  ): Behavior[Command] = {
    Behaviors.receiveMessage {
      case InitState(initialState) => {
        playerHandler match {
          // This case corresponds to the one where the PlayerHandler is new, becasue we received the Start message
          case Some(handler) => {
            val newDState = initialState.copy(handler)
            persistor ! PlayerPersistor.Persist(newDState)
            behaviour(
              PlayerState(
                // We override the PlayerHandler to the new one
                dState = newDState,
                tState = TransientPlayerState(Map.empty)
              ),
              persistor,
              eventProducer
            )
          }
          // This case corresponds to the one where the PlayerHandler is the same, so the Player is being restarted
          // While technically there is the chance that the PlayerHandler is still null here, it is very unlikely
          // and in case of failure the client should just reconnect and it should work (evenutally anyway)
          case None =>
            behaviour(
              PlayerState(
                dState = initialState,
                tState = TransientPlayerState(Map.empty)
              ),
              persistor,
              eventProducer
            )
        }
      }
      case Start(playerHandler) => {
        setupBehaviour(persistor, eventProducer, Some(playerHandler))
      }
      case _ => {
        // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
        Behaviors.same
      }
    }
  }

  def behaviour(
      state: PlayerState,
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: ActorRef[GameEventProducer.Command]
  ): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      msg match {
        case Move(velX, velY, replyTo) => {
          val newState = state.copy(
            dState = state.dState.copy(
              position = PlayerPosition(
                state.dState.position.x + (velX * 5),
                state.dState.position.y + (velY * 5)
              )
            )
          )
          replyTo ! PlayerHandler.MoveReply(
            newState.dState.position.x,
            newState.dState.position.y
          )
          eventProducer ! GameEventProducer.PlayerStateUpdate(newState.dState)
          behaviour(newState, persistor, eventProducer)
        }
        case PersistState() => {
          ctx.log.info(s"Persisting current state: ${state.dState}")
          persistor ! PlayerPersistor.Persist(state.dState)
          Behaviors.same
        }
        case UpdateEntityState(entityId, newEntityState) => {
          ctx.log.info(s"Updating entity state: $entityId")
          val newTState = state.tState.copy(
            knownGameEntities =
              state.tState.knownGameEntities + (entityId -> GameEntity(
                entityId,
                newEntityState
              ))
          )
          ctx.log.info(s"New tState: $newTState")

          state.dState.handler ! PlayerHandler.NotifyEntityStateUpdate(
            entityId,
            newEntityState
          )

          behaviour(
            state.copy(tState = newTState),
            persistor,
            eventProducer
          )
        }
        case Stop() => {
          ctx.log.info(s"Stopping player ${ctx.self.path.name}")
          Behaviors.stopped
        }
        // If the PlayerHandler failed and the client inits a new connection, we need to update the PlayerHandler
        case Start(playerHandler) => {
          behaviour(
            state.copy(dState = state.dState.copy(handler = playerHandler)),
            persistor,
            eventProducer
          )
        }
        case _ => {
          Behaviors.same // TODO throw error or something, it should not receive these messages again
        }
      }
    })
  }
}
