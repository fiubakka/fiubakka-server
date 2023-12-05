package server
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.protocol.client.PlayerAccepter

object GameServer {
  sealed trait Command
  final case class Run() extends Command

  def apply(): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      msg match {
        case Run() => {
          println("Game server is running...")
          ctx.spawn(PlayerAccepter(), "PlayerAccepter")
          Behaviors.same
        }
      }
    })
  }
}
