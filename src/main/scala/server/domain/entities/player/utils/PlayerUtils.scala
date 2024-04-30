package server.domain.entities.player.utils

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.player.Player
import server.domain.entities.player.Player._
import server.domain.structs.PlayerState
import server.protocol.event.GameEventConsumer
import server.protocol.event.GameEventProducer

import java.time.LocalDateTime
import scala.util.Random

object PlayerUtils {
  def getEventHandlers(ctx: ActorContext[Command], mapId: Int): (
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

  // This is a convinience function to be used by behaviors after the Player has finished initialization (ie. Running or Truco behaviors)
  def handleHeartbeatMessage(
      ctx: ActorContext[Command],
      msg: Heartbeat | CheckHeartbeat,
      state: PlayerState,
      updateBehaviorFunc: (state: PlayerState) => Behavior[Command],
      notifyStateToProducer: Boolean = true
  ): Behavior[Command] = {
    msg match {
      // We don't care about the PlayerHandler here, it should not change.
      case Heartbeat(_) => {
        updateBehaviorFunc(
          state.copy(tState =
            state.tState.copy(
              lastHeartbeatTime = LocalDateTime.now()
            )
          )
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
            if notifyStateToProducer then {
              state.tState.eventProducer ! GameEventProducer
                .PlayerStateUpdate(
                  state
                )
            }
            Behaviors.same
        }
      }
    }
  }

}
