package server

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import server.domain.entities.Player
import server.infra.PlayerPersistor
import server.protocol.GameEventConsumer
import server.protocol.GameEventProducer

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

    Sharding().init(Entity(typeKey = Player.TypeKey) { entityCtx =>
      Player(
        entityCtx.entityId,
        PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
      )
    })

    Sharding().init(Entity(typeKey = PlayerPersistor.TypeKey) { entityCtx =>
      PlayerPersistor(
        PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
      )
    })

    Sharding().init(Entity(typeKey = GameEventConsumer.TypeKey) { entityCtx =>
      GameEventConsumer(
        entityCtx.entityId,
        Sharding().entityRefFor(Player.TypeKey, entityCtx.entityId)
      )
    })

    Sharding().init(Entity(typeKey = GameEventProducer.TypeKey) { entityCtx =>
      GameEventProducer(
        entityCtx.entityId
      )
    })
  }
}
