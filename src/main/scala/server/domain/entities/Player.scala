package server.domain.entities

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect
import akka.serialization.jackson.CborSerializable

object Player {
  final case class Position(x: Int, y: Int)

  sealed trait Command extends CborSerializable
  // final case class Move(position: Position) extends Command
  final case class Move(posX: Int, posY: Int) extends Command
  final case class PrintPosition() extends Command

  final case class State(position: Position) extends CborSerializable

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String,
      persistenceId: PersistenceId
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.log.info(s"Starting player $entityId")
      DurableStateBehavior[Command, State](
        persistenceId,
        emptyState = State(Position(0, 0)),
        commandHandler = (state, command) => {
          command match {
            case Move(posX, posY) => {
              Effect.persist(
                state.copy(position =
                  Position(state.position.x + posX, state.position.y + posY)
                )
              )
            }
            case PrintPosition() => {
              println(s"Current position: ${state.position}")
              Effect.none
            }
          }
        }
      )
    }
  }
}
