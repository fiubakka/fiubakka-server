package server

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import server.domain.entities.Player

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

    sharding.get.init(Entity(typeKey = Player.TypeKey) { entityCtx =>
      Player(
        entityCtx.entityId,
        PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
      )
    })
  }
}
