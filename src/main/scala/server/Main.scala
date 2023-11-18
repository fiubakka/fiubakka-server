import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.Player
import server.GameServer
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  implicit val system: ActorSystem[GameServer.Command] =
    ActorSystem(GameServer(), "gameSystem")
  system ! GameServer.Run(system)
  Await.result(system.whenTerminated, Duration.Inf)
}
