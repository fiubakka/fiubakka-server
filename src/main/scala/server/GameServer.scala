package server

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import server.protocol.PlayerAccepter

object GameServer {
  sealed trait Command
  final case class Run(system: ActorSystem[Command]) extends Command

  def apply(): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      msg match {
        case Run(system) => {
          println("Game server is running...")
          ctx.spawn(PlayerAccepter(system), "playerAccepter")
          Behaviors.same
        }
      }
    })
  }
}
