package server.domain.entities

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Player {
  final case class Position(x: Int, y: Int)

  sealed trait Command
  final case class Move(position: Position) extends Command
  final case class PrintPosition() extends Command

  def apply(position: Position): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      ctx.log.info("Received message: {}", msg)

      msg match {
        case Move(newPosition) => apply(newPosition)
        case PrintPosition() => {
          ctx.log.info("Current position: {}", position)
          Behaviors.same
        }
      }
    })
  }
}
