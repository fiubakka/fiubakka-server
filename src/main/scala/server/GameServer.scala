package server
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.misc.Bot
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

          println("Spawning bots...")
          (1 to 20).foreach(i => ctx.spawn(Bot(), s"Bot$i"))
          Behaviors.same

        }
      }
    })
  }
}
