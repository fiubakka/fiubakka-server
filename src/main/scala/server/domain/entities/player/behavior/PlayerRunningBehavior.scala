package server.domain.entities.player.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerCommand._
import server.domain.entities.player.command.PlayerEventCommand._
import server.domain.entities.player.command.PlayerReplyCommand._
import server.domain.entities.player.utils.PlayerUtils
import server.domain.structs.PlayerState
import server.domain.structs.truco.TrucoMatchChallengeReplyEnum
import server.infra.PlayerPersistor
import server.protocol.event.GameEventProducer
import server.sharding.Sharding
import server.truco.TrucoManager

import java.time.LocalDateTime
import scala.concurrent.duration._

object PlayerRunningBehavior {
  def apply(
      state: PlayerState,
      persistor: EntityRef[PlayerPersistor.Command]
  ): Behavior[Player.Command] = {
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
            apply(newState, persistor)
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
                PlayerUtils.getEventHandlers(ctx, newMapId)

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

              apply(
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
            apply(newState, persistor)
          }

          case Stop() => {
            ctx.log.info(s"Stopping player ${ctx.self.path.name}")
            timers.cancelAll()
            state.tState.eventProducer ! GameEventProducer.PlayerDisconnect()
            persistor ! PlayerPersistor.Persist(state.dState)
            timers.startSingleTimer(StopReady(), 2.seconds)
            PlayerStopBehavior()
          }

          // If the PlayerHandler failed and the client inits a new connection, we need to update the PlayerHandler
          case Init(Player.InitData(newHandler, _)) => {
            state.tState.handler ! Ready(state.dState)
            apply(
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

          case BeginTrucoMatch(opponentUsername) => {
            val opponentPlayer =
              Sharding().entityRefFor(Player.TypeKey, opponentUsername)
            opponentPlayer ! AskBeginTrucoMatch(state.dState.playerName)
            Behaviors.same
          }

          case AskBeginTrucoMatch(opponentUsername) => {
            state.tState.handler ! NotifyAskBeginTrucoMatch(opponentUsername)
            Behaviors.same
          }

          case ReplyBeginTrucoMatch(opponentUsername, replyStatus) => {
            if replyStatus == TrucoMatchChallengeReplyEnum.Accepted then {
              // If the TrucoManager fails to establish connection with the players, it will suicide itself
              ctx.spawn(
                TrucoManager(opponentUsername, state.dState.playerName),
                s"TrucoManager-${state.dState.playerName}-${opponentUsername}"
              )
            } else {
              val opponentPlayer =
                Sharding().entityRefFor(Player.TypeKey, opponentUsername)
              opponentPlayer ! BeginTrucoMatchDenied(opponentUsername)
            }
            Behaviors.same
          }

          case BeginTrucoMatchDenied(opponentUsername) => {
            state.tState.handler ! NotifyBeginTrucoMatchDenied(
              opponentUsername
            )
            Behaviors.same
          }

          case SyncTrucoMatchStart(trucoManager) => {
            ctx.log.info(
              "Starting Truco match, completed handshake with TrucoManager!"
            )
            PlayerTrucoBehavior(state, trucoManager)
          }

          // We don't care about the PlayerHandler here, it should not change.
          case Heartbeat(_) => {
            apply(
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

  private def handleConsumerMessage(
      msg: Player.EventCommand,
      state: PlayerState
  ): Behavior[Player.Command] = {
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
}
