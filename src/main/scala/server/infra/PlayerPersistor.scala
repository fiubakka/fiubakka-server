package server.infra

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect
import akka.serialization.jackson.CborSerializable
import server.domain.structs.PlayerPosition
import server.domain.structs.PlayerState

object PlayerPersistor {
  sealed trait Command extends CborSerializable
  final case class Persist(newState: PlayerState) extends Command
  final case class GetState(replyTo: ActorRef[GetStateResponse]) extends Command

  final case class GetStateResponse(state: PlayerState) extends CborSerializable

  val TypeKey = EntityTypeKey[Command]("PlayerPersistor")

  def apply(persistenceId: PersistenceId): Behavior[Command] = {
    DurableStateBehavior[Command, PlayerState](
      persistenceId,
      emptyState = PlayerState(PlayerPosition(0, 0)),
      commandHandler = commandHandler
    )
  }

  private def commandHandler(
      state: PlayerState,
      command: Command
  ): Effect[PlayerState] = {
    command match {
      case Persist(newState) => {
        Effect.persist(newState)
      }
      case GetState(replyTo) => {
        replyTo ! GetStateResponse(state)
        Effect.none
      }
    }
  }
}
