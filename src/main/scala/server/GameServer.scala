package server
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.misc.Bot
import server.protocol.client.PlayerAccepter
import server.protocol.event.kafka.KafkaConsumer
import server.protocol.event.kafka.KafkaProducer

object GameServer {
  sealed trait Command
  final case class Run() extends Command

  def apply(): Behavior[Command] = {
    Behaviors.setup { ctx =>
      KafkaProducer.configure(ctx.system)
      KafkaConsumer.configure(ctx)

      Behaviors.receive((ctx, msg) => {
        msg match {
          case Run() => {
            println("Game server is running...")
            ctx.spawn(PlayerAccepter(), "PlayerAccepter")

            println("Spawning bots...")
            (1 to 2).foreach(i => ctx.spawn(Bot(), s"Bot$i"))

            Behaviors.same

          }
        }
      })
    }
  }
}
