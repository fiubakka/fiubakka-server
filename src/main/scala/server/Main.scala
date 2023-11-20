import akka.actor.typed.ActorSystem
import server.GameServer
import server.Sharding

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  implicit val system: ActorSystem[GameServer.Command] =
    ActorSystem(GameServer(), "GameSystem")
  Sharding.configure(system)

  system ! GameServer.Run()
  Await.result(system.whenTerminated, Duration.Inf)
}
