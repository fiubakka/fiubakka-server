import akka.actor.typed.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.rollingupdate.kubernetes.AppVersionRevision
import akka.rollingupdate.kubernetes.PodDeletionCost
import server.GameServer
import server.infra.DB
import server.sharding.Sharding

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  implicit val system: ActorSystem[GameServer.Command] =
    ActorSystem(GameServer(), "fiubakka-server")
  DB.configure()
  Sharding.configure(system)

  // Only needed for Kubernetes
  if sys.env.getOrElse("ENV", "") == "production" then {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    PodDeletionCost(system).start()
    AppVersionRevision(system).start()
  }

  // KafkaProducer.configure(system)
  // KafkaConsumer.configure(system)

  system ! GameServer.Run()
  Await.result(system.whenTerminated, Duration.Inf)
}
