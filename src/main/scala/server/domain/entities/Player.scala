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
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Random
import scala.util.Success

final case class InitData(
    handler: ActorRef[Player.ReplyCommand],
    equipment: Option[Equipment]
)

object Player {
  sealed trait Command extends CborSerializable
  sealed trait ReplyCommand extends CborSerializable
  sealed trait EventCommand extends CborSerializable

  // Command

  final case class Init(initialData: InitData) extends Command

  // We also send to PlayerHandler ref in case the Player switches cluster nodes or dies
  // and starts again. In this case we would be stuck waiting for an Init message from the
  // PlayerHandler that would never arrive! By relying on this fallback message, we fix this issue.
  //
  // We still keep the Init message to avoid including other init data (ie. the equipment) being included
  // in the Heartbeat message and having a more complex message structure.
  final case class Heartbeat(handler: ActorRef[Player.ReplyCommand])
      extends Command
  final case class CheckHeartbeat() extends Command

  final case class Stop() extends Command
  final case class StopReady() extends Command

  final case class InitialState(
      initialState: DurablePlayerState
  ) extends Command

  final case class Move(
      velocity: Velocity,
      position: Position
  ) extends Command

  final case class PersistState() extends Command

  final case class AddMessage(
      msg: String
  ) extends Command
  final case class UpdateEquipment(
      equipment: Equipment
  ) extends Command
  final case class ChangeMap(
      newMapId: Int
  ) extends Command

  // GameEventConsumer messages

  final case class ReceiveMessage(
      entityId: String,
      msg: String
  ) extends EventCommand
  final case class UpdateEntityState(
      entityId: String,
      newEntityState: GameEntityState
  ) extends EventCommand
  final case class EntityDisconnect(
      entityId: String
  ) extends EventCommand
  final case class GameEventConsumerCommand(
      command: EventCommand,
      consumerRef: ActorRef[GameEventConsumer.Command]
  ) extends Command

  // PlayerHandler reply messages

  final case class NotifyEntityStateUpdate(
      entityId: String,
      newEntityState: GameEntityState
  ) extends ReplyCommand
  final case class NotifyMessageReceived(
      entityId: String,
      msg: String
  ) extends ReplyCommand
  final case class NotifyEntityDisconnect(
      entityId: String
  ) extends ReplyCommand
  final case class ReplyStop() extends ReplyCommand
  final case class Ready(initialState: DurablePlayerState) extends ReplyCommand
  final case class ChangeMapReady(newMapId: Int) extends ReplyCommand

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

        initBehaviour(entityId, persistor)
      }
    }
  }

  def initBehaviour(
      entityId: String,
      persistor: EntityRef[PlayerPersistor.Command],
      initialData: Option[InitData] = None
  ): Behavior[Command] = {
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
                    InitData(handler, None),
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
          initBehaviour(entityId, persistor, Some(initData))
        }

        // We know that this message will only be recieved if the PlayerHandler is in
        // the running state, so its safe to assume we won't receive an Init message in this case
        // and instead set this sender as the PlayerHandler and complete intialization.
        // This would be what should happen if the Player dies or is moved to another node.
        case Heartbeat(handler) => {
          initBehaviour(
            entityId,
            persistor,
            Some(InitData(handler, None))
          ) // No equipment because the Player should already be registered
        }

        case _ => {
          // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
          Behaviors.same
        }
      }
    }
  }

  def runningBehaviour(
      state: PlayerState,
      persistor: EntityRef[PlayerPersistor.Command]
  ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      Behaviors.withTimers { timers =>
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
            state.tState.eventProducer ! GameEventProducer.PlayerStateUpdate(
              newState
            )
            runningBehaviour(newState, persistor)
          }

          case PersistState() => {
            ctx.log.debug(s"Persisting current state: ${state.dState}")
            persistor ! PlayerPersistor.Persist(state.dState)
            Behaviors.same
          }

          case GameEventConsumerCommand(command, consumerRef) => {
            consumerRef match {
              case state.tState.eventConsumer => {
                handleConsumerMessage(command, state)
              }
              // If the consumer doesn't match, it means it corresponds to the previous Map consumer buffered messages. Ignore it.
              case _ => {
                Behaviors.same
              }
            }
          }

          case AddMessage(msg) => {
            state.tState.eventProducer ! GameEventProducer.AddMessage(msg)
            Behaviors.same
          }

          case ChangeMap(newMapId) => {
            ctx.log.info(
              s"Changing ${ctx.self.path.name} from map ${state.dState.mapId} to $newMapId"
            )
            if newMapId == state.dState.mapId then {
              state.tState.handler ! ChangeMapReady(newMapId)
              Behaviors.same
            } else {
              ctx.stop(state.tState.eventConsumer)
              ctx.stop(state.tState.eventProducer)

              val (newEventConsumer, newEventProducer) =
                getEventHandlers(ctx, newMapId)

              val newState = state.copy(
                dState = state.dState.copy(
                  mapId = newMapId
                ),
                tState = state.tState.copy(
                  eventProducer = newEventProducer,
                  eventConsumer = newEventConsumer
                )
              )

              persistor ! PlayerPersistor.Persist(newState.dState)
              state.tState.handler ! ChangeMapReady(newMapId)

              runningBehaviour(
                newState,
                persistor
              )
            }
          }

          case UpdateEquipment(equipment) => {
            val newState = state.copy(
              dState = state.dState.copy(
                equipment = equipment
              )
            )
            persistor ! PlayerPersistor.Persist(newState.dState)
            state.tState.eventProducer ! GameEventProducer.PlayerStateUpdate(
              newState
            )
            runningBehaviour(newState, persistor)
          }

          case Stop() => {
            ctx.log.info(s"Stopping player ${ctx.self.path.name}")
            timers.cancelAll()
            state.tState.eventProducer ! GameEventProducer.PlayerDisconnect()
            persistor ! PlayerPersistor.Persist(state.dState)
            timers.startSingleTimer(StopReady(), 2.seconds)
            stoppingBehaviour()
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
              persistor
            )
          }

          // We don't care about the PlayerHandler here, it should not change.
          case Heartbeat(_) => {
            runningBehaviour(
              state.copy(tState =
                state.tState.copy(
                  lastHeartbeatTime = LocalDateTime.now()
                )
              ),
              persistor
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
                state.tState.eventProducer ! GameEventProducer
                  .PlayerDisconnect()
                state.tState.handler ! ReplyStop() // Player handler it's most likely dead but just in case
                Behaviors.stopped
              case false =>
                state.tState.eventProducer ! GameEventProducer
                  .PlayerStateUpdate(
                    state
                  )
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
  }

  def stoppingBehaviour(): Behavior[Command] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case StopReady() => {
          Behaviors.stopped
        }

        case _ => {
          Behaviors.same
        }
      }
    }
  }

  private def handleConsumerMessage(
      msg: EventCommand,
      state: PlayerState
  ): Behavior[Command] = {
    msg match {
      case ReceiveMessage(entityId, msg) => {
        state.tState.handler ! NotifyMessageReceived(
          entityId,
          msg
        )
        Behaviors.same
      }

      case UpdateEntityState(entityId, newEntityState) => {
        state.tState.handler ! NotifyEntityStateUpdate(
          entityId,
          newEntityState
        )

        Behaviors.same
      }

      case EntityDisconnect(entityId) => {
        state.tState.handler ! NotifyEntityDisconnect(entityId)
        Behaviors.same
      }
    }
  }

  private def finishInitialization(
      ctx: ActorContext[Command],
      persistor: EntityRef[PlayerPersistor.Command],
      initialData: InitData,
      initialState: DurablePlayerState
  ): Behavior[Command] = {
    val InitData(handler, equipment) = initialData
    var newState = initialState

    if equipment.isDefined then { // If it's a new player
      // In practice, the persistor entityId is the playername, so set it here
      newState = initialState.copy(
        playerName = persistor.entityId,
        equipment = equipment.get
      )
    }

    val (eventConsumer, eventProducer) =
      getEventHandlers(ctx, initialState.mapId)

    handler ! Ready(newState)
    runningBehaviour(
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

  private def getEventHandlers(ctx: ActorContext[Command], mapId: Int): (
      ActorRef[GameEventConsumer.Command],
      ActorRef[GameEventProducer.Command]
  ) = {
    val eventHandlersSuffix = Random.alphanumeric.take(8).mkString
    val eventConsumer = ctx.spawn(
      GameEventConsumer(
        ctx.self,
        mapId
      ),
      s"GameEventConsumer-$mapId-$eventHandlersSuffix"
    )
    val eventProducer = ctx.spawn(
      GameEventProducer(
        ctx.self.path.name,
        mapId
      ),
      s"GameEventProducer-$mapId-$eventHandlersSuffix"
    )
    (eventConsumer, eventProducer)
  }
}
