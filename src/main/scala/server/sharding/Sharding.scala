package server.sharding

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import server.domain.entities.Player
import server.infra.PlayerPersistor

object Sharding {
  private var sharding: Option[ClusterSharding] = None

  def apply(): ClusterSharding = {
    sharding.getOrElse(
      throw new IllegalStateException(
        "Sharding not initialized. Call configure method first."
      )
    )
  }

  // TODO It's possible to configure the PlayerPersistor shard to live in the same node of the Player shard.
  // See https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#colocate-shards
  def configure(system: ActorSystem[?]) = {
    sharding = Some(ClusterSharding(system))

    Sharding().init(
      Entity(typeKey = Player.TypeKey) { entityCtx =>
        Player(
          entityCtx.entityId
        )
      }
    )

    Sharding().init(Entity(typeKey = PlayerPersistor.TypeKey) { entityCtx =>
      PlayerPersistor(
        PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
      )
    })
  }
}
