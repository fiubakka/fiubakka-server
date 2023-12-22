import akka.actor.typed.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import server.GameServer
import server.sharding.Sharding

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  implicit val system: ActorSystem[GameServer.Command] =
    ActorSystem(GameServer(), "fiubakka-server")
  Sharding.configure(system)
  // Only needed for Kubernetes
  if (sys.env.getOrElse("ENV", "") == "production") {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
  }

  system ! GameServer.Run()
  Await.result(system.whenTerminated, Duration.Inf)
}
