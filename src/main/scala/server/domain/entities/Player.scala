package server.domain.entities

import akka.serialization.jackson.CborSerializable
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect

object Player {
  final case class Position(x: Int, y: Int)

  sealed trait Command extends CborSerializable
  final case class Move(position: Position) extends Command
  final case class PrintPosition() extends Command

  final case class State(position: Position) extends CborSerializable

  def apply(persistenceId: PersistenceId): DurableStateBehavior[Command, State] = {
    DurableStateBehavior.apply[Command, State](
      persistenceId,
      emptyState = State(Position(0, 0)),
      commandHandler = (state, command) => {
        command match {
          case Move(newPosition) => {
            Effect.persist(state.copy(position = newPosition))
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