package server

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

object Sharding {
  private var sharding: Option[ClusterSharding] = None

  def apply(): ClusterSharding = {
    sharding.getOrElse(
      throw new IllegalStateException(
        "Sharding not initialized. Call configure method first."
      )
    )
  }

  def configure(system: ActorSystem[_]) = {
    sharding = Some(ClusterSharding(system))
  }
}
