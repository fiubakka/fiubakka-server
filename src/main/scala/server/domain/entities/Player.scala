package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.serialization.jackson.CborSerializable
import akka.util.Timeout
import server.domain.structs.DurablePlayerState
import server.domain.structs.GameEntity
import server.domain.structs.GameEntityState
import server.domain.structs.PlayerState
import server.domain.structs.TransientPlayerState
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.infra.PlayerPersistor
import server.protocol.event.GameEventConsumer
import server.protocol.event.GameEventProducer
import server.sharding.Sharding

import java.time.LocalDateTime
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

final case class InitData(
    handler: ActorRef[Player.ReplyCommand],
    equipment: Option[Equipment]
)

object Player {
  sealed trait Command extends CborSerializable
  sealed trait ReplyCommand extends CborSerializable

  // Command

  final case class Init(initialData: InitData) extends Command

  final case class Heartbeat() extends Command
  final case class CheckHeartbeat() extends Command

  final case class Stop() extends Command

  final case class InitialState(
      initialState: DurablePlayerState
  ) extends Command

  final case class Move(
      velocity: Velocity,
      position: Position
  ) extends Command
  final case class AddMessage(
      msg: String
  ) extends Command
  final case class ReceiveMessage(
      entityId: String,
      msg: String
  ) extends Command
  final case class UpdateEntityState(
      entityId: String,
      newEntityState: GameEntityState
  ) extends Command
  final case class PersistState() extends Command
  final case class ChangeMap(
      newMapId: Int
  ) extends Command

  // ReplyCommand

  final case class NotifyEntityStateUpdate(
      entityId: String,
      newEntityState: GameEntityState
  ) extends ReplyCommand
  final case class NotifyMessageReceived(
      entityId: String,
      msg: String
  ) extends ReplyCommand
  final case class ReplyStop() extends ReplyCommand
  final case class Ready(initialState: DurablePlayerState) extends ReplyCommand
  final case class ChangeMapReady(newMapId: Int) extends ReplyCommand

  Map[String, GameEntity]()

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val askTimeout = Timeout(30.seconds)

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

        initBehaviour(persistor, ctx, entityId)
      }
    }
  }

  def initBehaviour(
      persistor: EntityRef[PlayerPersistor.Command],
      ctx: ActorContext[Command],
      entityId: String,
      initialData: Option[InitData] = None
  ): Behavior[Command] = {
    Behaviors.receiveMessage {

      case InitialState(initialState) => {
        val eventConsumer = ctx.spawn(
          GameEventConsumer(
            entityId,
            ctx.self,
            initialState.mapId
          ),
          s"GameEventConsumer-$entityId"
        )

        val eventProducer = ctx.spawn(
          GameEventProducer(
            entityId,
            initialState.mapId
          ),
          s"GameEventProducer-$entityId"
        )
        initialData match {
          case Some(initialData) =>
            finishInitialization(
              persistor,
              eventProducer,
              eventConsumer,
              initialData,
              initialState
            )

          case None => {
            Behaviors.receiveMessage {
              case Init(initialData) =>
                finishInitialization(
                  persistor,
                  eventProducer,
                  eventConsumer,
                  initialData,
                  initialState
                )

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
        initBehaviour(persistor, ctx, entityId, Some(initData))
      }

      case _ => {
        // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
        Behaviors.same
      }
    }
  }

  def runningBehaviour(
      state: PlayerState,
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: ActorRef[GameEventProducer.Command],
      eventConsumer: ActorRef[GameEventConsumer.Command]
  ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Move(newVelocity, newPosition) => {
          val newState = state.copy(
            dState = state.dState.copy(
              position = newPosition
            ),
            tState = state.tState.copy(
              velocity = newVelocity
            )
          )
          eventProducer ! GameEventProducer.PlayerStateUpdate(newState)
          runningBehaviour(newState, persistor, eventProducer, eventConsumer)
        }

        case PersistState() => {
          ctx.log.debug(s"Persisting current state: ${state.dState}")
          persistor ! PlayerPersistor.Persist(state.dState)
          Behaviors.same
        }

        case UpdateEntityState(entityId, newEntityState) => {
          state.tState.handler ! NotifyEntityStateUpdate(
            entityId,
            newEntityState
          )

          Behaviors.same
        }

        case AddMessage(msg) => {
          eventProducer ! GameEventProducer.AddMessage(msg)
          Behaviors.same
        }

        case ReceiveMessage(entityId, msg) => {
          state.tState.handler ! NotifyMessageReceived(
            entityId,
            msg
          )
          Behaviors.same
        }

        case ChangeMap(newMapId) => {
          ctx.stop(eventConsumer)
          ctx.stop(eventProducer)

          // TODO: Add random to the new consumer/producer names to avoid conflicts
          val newEventConsumer = ctx.spawn(
            GameEventConsumer(ctx.self.path.name, ctx.self, newMapId),
            s"GameEventConsumer-${ctx.self.path.name}-$newMapId"
          )

          val newEventProducer = ctx.spawn(
            GameEventProducer(ctx.self.path.name, newMapId),
            s"GameEventProducer-${ctx.self.path.name}-$newMapId"
          )

          val newState = state.copy(
            dState = state.dState.copy(
              mapId = newMapId
            )
          )

          persistor ! PlayerPersistor.Persist(newState.dState)
          state.tState.handler ! ChangeMapReady(newMapId)

          runningBehaviour(
            newState,
            persistor,
            newEventProducer,
            newEventConsumer
          )
        }

        case Stop() => {
          ctx.log.info(s"Stopping player ${ctx.self.path.name}")
          persistor ! PlayerPersistor.Persist(state.dState)
          Behaviors.stopped
        }

        // If the PlayerHandler failed and the client inits a new connection, we need to update the PlayerHandler
        case Init(InitData(newHandler, _)) => {
          state.tState.handler ! Ready(state.dState)
          runningBehaviour(
            state.copy(tState =
              state.tState.copy(
                lastHeartbeatTime =
                  LocalDateTime.now(), // Just in case optimization
                handler = newHandler
              )
            ),
            persistor,
            eventProducer,
            eventConsumer
          )
        }

        case Heartbeat() => {
          runningBehaviour(
            state.copy(tState =
              state.tState.copy(
                lastHeartbeatTime = LocalDateTime.now()
              )
            ),
            persistor,
            eventProducer,
            eventConsumer
          )
        }

        case CheckHeartbeat() => {
          val lastHeartbeatTime = state.tState.lastHeartbeatTime
          val heartStopped =
            LocalDateTime.now().isAfter(lastHeartbeatTime.plusSeconds(10))
          heartStopped match {
            case true =>
              ctx.log.warn(
                s"Player ${ctx.self.path.name} has not sent a heartbeat in the last 10 seconds, disconnecting"
              )
              state.tState.handler ! ReplyStop() // Player handler it's most likely dead but just in case
              Behaviors.stopped
            case false =>
              eventProducer ! GameEventProducer.PlayerStateUpdate(state)
              Behaviors.same
          }
        }

        case _ => {
          ctx.log.error(s"Received unexpected message while running: $msg")
          Behaviors.same
        }
      }
    }
  }

  private def finishInitialization(
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: ActorRef[GameEventProducer.Command],
      eventConsumer: ActorRef[GameEventConsumer.Command],
      initialData: InitData,
      initialState: DurablePlayerState
  ): Behavior[Command] = {
    val InitData(handler, equipment) = initialData
    var newState = initialState

    if (equipment.isDefined) { // If it's a new player
      // In practice, the persistor entityId is the playername, so set it here
      newState = initialState.copy(
        playerName = persistor.entityId,
        equipment = equipment.get
      )
    }

    handler ! Ready(newState)
    runningBehaviour(
      PlayerState(
        newState,
        tState = TransientPlayerState(
          handler,
          LocalDateTime.now(),
          Velocity(0, 0)
        )
      ),
      persistor,
      eventProducer,
      eventConsumer
    )
  }
}
