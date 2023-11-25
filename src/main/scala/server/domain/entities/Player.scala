package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect
import akka.serialization.jackson.CborSerializable
import server.protocol.PlayerHandler

import scala.concurrent.duration

object Player {
  final case class Position(x: Int, y: Int)

  sealed trait Command extends CborSerializable
  final case class Move(
      velX: Int,
      velY: Int,
      replyTo: ActorRef[PlayerHandler.Command]
  ) extends Command
  final case class PrintPosition() extends Command
  final case class PersistState() extends Command

  final case class State(position: Position) extends CborSerializable

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String,
      persistenceId: PersistenceId
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(
          "persist",
          PersistState(),
          duration.FiniteDuration(30, "second")
        )
        ctx.log.info(s"Starting player $entityId")
        DurableStateBehavior[Command, State](
          persistenceId,
          emptyState = State(Position(0, 0)),
          commandHandler = (state, command) => {
            command match {
              case Move(velX, velY, replyTo) => {
                Effect
                  .persist(
                    state.copy(position =
                      Position(
                        state.position.x + (velX * 20),
                        state.position.y + (velY * 20)
                      )
                    )
                  )
                  .thenReply(replyTo) { newState =>
                    PlayerHandler
                      .MoveReply(newState.position.x, newState.position.y)
                  }
              }
              case PrintPosition() => {
                println(s"Current position: ${state.position}")
                Effect.none
              }
              case PersistState() => {
                println(s"Persisting current state: ${state}")
                Effect
                  .persist(
                    state.copy(position =
                      Position(
                        state.position.x,
                        state.position.y
                      )
                    )
                  )
              }
            }
          }
        )
      }
    }
  }
}
