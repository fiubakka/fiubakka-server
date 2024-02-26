package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
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

object Player {
  sealed trait Command extends CborSerializable
  sealed trait ReplyCommand extends CborSerializable

  // Command

  final case class Init(handler: ActorRef[ReplyCommand]) extends Command

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
            InitialState(initialState)
          }
          case Failure(ex) => {
            ctx.log.error(s"Failed to get player state: $ex")
            throw ex
          }
        }

        initBehaviour(persistor, eventProducer)
      }
    }
  }

  def initBehaviour(
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: ActorRef[GameEventProducer.Command],
      handler: Option[ActorRef[ReplyCommand]] = None
  ): Behavior[Command] = {
    Behaviors.receiveMessage {

      case InitialState(initialState) => {
        handler match {
          case Some(handler) =>
            handler ! Ready(initialState)
            runningBehaviour(
              PlayerState(
                initialState,
                tState = TransientPlayerState(
                  handler,
                  LocalDateTime.now(),
                  Velocity(0, 0)
                )
              ),
              persistor,
              eventProducer
            )

          case None => {
            Behaviors.receiveMessage {
              case Init(handler) => {
                handler ! Ready(initialState)
                runningBehaviour(
                  PlayerState(
                    initialState,
                    tState = TransientPlayerState(
                      handler,
                      LocalDateTime.now(),
                      Velocity(0, 0)
                    )
                  ),
                  persistor,
                  eventProducer
                )
              }

              // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
              case _ => {
                Behaviors.same
              }
            }
          }
        }
      }

      // Optimization to avoid waiting for the second Init message to start
      case Init(handler) => {
        initBehaviour(persistor, eventProducer, Some(handler))
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
      eventProducer: ActorRef[GameEventProducer.Command]
  ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(
          "checkHeartbeat",
          CheckHeartbeat(),
          5.seconds
        )

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
            runningBehaviour(newState, persistor, eventProducer)
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

          case Stop() => {
            ctx.log.info(s"Stopping player ${ctx.self.path.name}")
            persistor ! PlayerPersistor.Persist(state.dState)
            Behaviors.stopped
          }

          // If the PlayerHandler failed and the client inits a new connection, we need to update the PlayerHandler
          case Init(newHandler) => {
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
              eventProducer
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
              eventProducer
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
            Behaviors.same // TODO throw error or something, it should not receive these messages again
          }
        }
      }
    }
  }
}
