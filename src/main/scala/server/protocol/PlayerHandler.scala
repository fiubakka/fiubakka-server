package server.protocol

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

object PlayerHandler {
  sealed trait Command
  final case class Run() extends Command

  // def apply(): Behavior[Command] = {
  //   Behaviors.setup(ctx => {
  //     val player = ctx.spawn(Player(Player.Position(1, 2)), "mainPlayer")

  //     Behaviors.receiveMessage {
  //       case Run() => {
  //         println("Game server is running...")
  //         player ! Player.PrintPosition()
  //         Behaviors.same
  //       }
  //     }
  //   })
  // }
}
