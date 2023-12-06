package server.infra

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect
import akka.serialization.jackson.CborSerializable
import server.domain.structs.DurablePlayerState
import server.domain.structs.PlayerPosition

object PlayerPersistor {
  sealed trait Command extends CborSerializable
  final case class Persist(newState: DurablePlayerState) extends Command
  final case class GetState(replyTo: ActorRef[GetStateResponse]) extends Command

  final case class GetStateResponse(state: DurablePlayerState)
      extends CborSerializable

  val TypeKey = EntityTypeKey[Command]("PlayerPersistor")

  def apply(persistenceId: PersistenceId): Behavior[Command] = {
    DurableStateBehavior[Command, DurablePlayerState](
      persistenceId,
      emptyState = DurablePlayerState(PlayerPosition(0, 0)),
      commandHandler = commandHandler
    )
  }

  private def commandHandler(
      state: DurablePlayerState,
      command: Command
  ): Effect[DurablePlayerState] = {
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
