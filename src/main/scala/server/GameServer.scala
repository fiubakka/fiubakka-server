package server
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.misc.Bot
import server.protocol.client.PlayerAccepter

import scala.concurrent.duration.DurationInt

object GameServer {
  sealed trait Command
  final case class Run() extends Command
  final case class SpawnBot(number: Int) extends Command

  def apply(): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.receive((ctx, msg) => {
        val maxBots = ctx.system.settings.config.getInt("game.bots.perNode")
        val delayPerBotCreation =
          ctx.system.settings.config.getInt("game.bots.perCreationDelaySeconds")

        msg match {
          case Run() => {
            timers.startSingleTimer(SpawnBot(0), 20.second)
            ctx.log.info("Game server is running...")
            ctx.spawn(PlayerAccepter(), "PlayerAccepter")
            Behaviors.same
          }

          case SpawnBot(number) => {
            if number < maxBots then {
              ctx.spawn(Bot(), s"Bot$number")
              timers.startSingleTimer(
                SpawnBot(number + 1),
                delayPerBotCreation.second
              )
            }
            Behaviors.same
          }
        }
      })
    }
  }
}
