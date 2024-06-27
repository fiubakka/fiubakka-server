package server.domain.entities.player.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerActionCommand.*
import server.domain.entities.player.command.PlayerEventCommand.*
import server.domain.entities.player.command.PlayerReplyCommand.*
import server.domain.entities.player.utils.PlayerUtils
import server.domain.entities.truco.TrucoManager
import server.domain.structs.PlayerState
import server.domain.structs.truco.TrucoMatchChallengeReplyEnum
import server.infra.PlayerPersistor
import server.protocol.event.GameEventProducer
import server.sharding.Sharding

import java.time.LocalDateTime
import scala.concurrent.duration.*

object PlayerRunningBehavior {
  def apply(
      state: PlayerState
  ): Behavior[Player.Command] = {
    Behaviors.receive { (ctx, msg) =>
      Behaviors.withTimers { timers =>
        state.tState.metrics.incrementMessageCount()

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
            apply(newState)
          }

          case PersistState() => {
            ctx.log.debug(s"Persisting current state: ${state.dState}")
            state.tState.persistor ! PlayerPersistor.Persist(state.dState)
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

          case GameEventConsumerFailure(consumerRef, errorMsg) => {
            // If the consumers do not match its ok to ignore the error, since its an older consumer (ie. changing maps)
            if state.tState.eventConsumer == consumerRef then {
              ctx.log.error(
                s"Failure in GameEventConsumer for player ${state.dState.playerName} and consumer ${consumerRef}: $errorMsg"
              )
              ctx.log.info(
                s"Restarting consumer and producer for player ${state.dState.playerName}"
              )

              ctx.stop(state.tState.eventConsumer)
              ctx.stop(state.tState.eventProducer)

              val (newEventConsumer, newEventProducer) =
                PlayerUtils.getEventHandlers(ctx, state.dState.mapId)
              val newState = state.copy(
                tState = state.tState.copy(
                  eventProducer = newEventProducer,
                  eventConsumer = newEventConsumer
                )
              )
              state.tState.persistor ! PlayerPersistor.Persist(newState.dState)

              apply(newState)
            } else {
              Behaviors.same
            }

          }

          case AddMessage(msg) => {
            state.tState.eventProducer ! GameEventProducer.AddMessage(msg)
            Behaviors.same
          }

          case ChangeMap(newMapId) => {
            if newMapId == state.dState.mapId then {
              state.tState.handler ! ChangeMapReady(newMapId)
              Behaviors.same
            } else {
              ctx.log.info(
                s"Changing ${ctx.self.path.name} from map ${state.dState.mapId} to $newMapId"
              )
              ctx.stop(state.tState.eventConsumer)
              ctx.stop(state.tState.eventProducer)

              val (newEventConsumer, newEventProducer) =
                PlayerUtils.getEventHandlers(ctx, newMapId)

              val newState = state.copy(
                tState = state.tState.copy(
                  eventProducer = newEventProducer,
                  eventConsumer = newEventConsumer
                ),
                dState = state.dState.copy(
                  mapId = newMapId
                )
              )

              state.tState.persistor ! PlayerPersistor.Persist(newState.dState)
              state.tState.handler ! ChangeMapReady(newMapId)

              apply(
                newState
              )
            }
          }

          case UpdateEquipment(equipment) => {
            val newState = state.copy(
              dState = state.dState.copy(
                equipment = equipment
              )
            )
            state.tState.persistor ! PlayerPersistor.Persist(newState.dState)
            state.tState.eventProducer ! GameEventProducer.PlayerStateUpdate(
              newState
            )
            apply(newState)
          }

          case Stop() => {
            ctx.log.info(s"Stopping player ${ctx.self.path.name}")
            timers.cancelAll()
            state.tState.eventProducer ! GameEventProducer.PlayerDisconnect()
            state.tState.persistor ! PlayerPersistor.Persist(state.dState)
            timers.startSingleTimer(StopReady(), 2.seconds)
            PlayerStoppingBehavior()
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
              )
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
            trucoManager ! TrucoManager.PlayerSyncedTrucoMatchStart(
              state.dState.playerName
            )
            state.tState.eventProducer ! GameEventProducer
              .PlayerDisconnect() // Removes player from map
            ctx.stop(state.tState.eventConsumer)
            // We don't stop the Producer because we need to send the PlayerDisconnect event
            // and it doesnt add any unnecessary overhead
            PlayerTrucoBehavior(state, trucoManager)
          }

          case heartMessage @ (Heartbeat(_) | CheckHeartbeat()) => {
            PlayerUtils.handleHeartbeatMessage(ctx, heartMessage, state, apply)
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
