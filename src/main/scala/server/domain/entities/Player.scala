package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.serialization.jackson.CborSerializable
import akka.util.Timeout
import server.Sharding
import server.domain.structs.PlayerPosition
import server.domain.structs.PlayerState
import server.infra.PlayerPersistor
import server.protocol.GameEventProducer
import server.protocol.PlayerHandler

import scala.concurrent.duration
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object Player {

  sealed trait Command extends CborSerializable
  final case class InitState(
      initialState: PlayerState
  ) extends Command
  final case class Move(
      velX: Int,
      velY: Int,
      replyTo: ActorRef[PlayerHandler.MoveReply]
  ) extends Command
  final case class PrintPosition() extends Command
  final case class PersistState() extends Command

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String,
      persistenceId: PersistenceId
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val askTimeout = Timeout(5.seconds)

        timers.startTimerAtFixedRate(
          "persist",
          PersistState(),
          duration.FiniteDuration(5, "second")
        )
        ctx.log.info(s"Starting player $entityId")

        Sharding().init(Entity(typeKey = PlayerPersistor.TypeKey) { entityCtx =>
          PlayerPersistor(
            PersistenceId(entityCtx.entityTypeKey.name, entityId)
          )
        })

        val persistor = Sharding().entityRefFor(
          PlayerPersistor.TypeKey,
          entityId
        )

        val eventProducer = Sharding().entityRefFor(
          GameEventProducer.TypeKey,
          "player2"
        )

        ctx.ask(
          persistor,
          PlayerPersistor.GetState.apply
        ) {
          case Success(PlayerPersistor.GetStateResponse(initialState)) => {
            InitState(initialState)
          }
          case Failure(ex) => {
            ctx.log.error(s"Failed to get state: $ex")
            InitState(
              PlayerState(PlayerPosition(0, 0))
            ) // TODO throw error or something
          }
        }

        setupBehaviour(persistor, eventProducer)
      }
    }
  }

  def setupBehaviour(
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: EntityRef[GameEventProducer.Command]
  ): Behavior[Command] = {
    Behaviors.receiveMessage {
      case InitState(initialState) => {
        behaviour(initialState, persistor, eventProducer)
      }
      case _ => {
        // Ignores all other messages until the state is initialized (synced with PlayerPersistor)
        Behaviors.same
      }
    }
  }

  def behaviour(
      state: PlayerState,
      persistor: EntityRef[PlayerPersistor.Command],
      eventProducer: EntityRef[GameEventProducer.Command]
  ): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      msg match {
        case InitState(_) => {
          Behaviors.same // TODO throw error or something, it should not receive this message again
        }
        case Move(velX, velY, replyTo) => {
          val newState = state.copy(
            position = PlayerPosition(
              state.position.x + (velX * 20),
              state.position.y + (velY * 20)
            )
          )
          replyTo ! PlayerHandler.MoveReply(
            newState.position.x,
            newState.position.y
          )
          eventProducer ! GameEventProducer.ProduceEvent(
            s"Moved to position (${newState.position.x}, ${newState.position.y})"
          )
          behaviour(newState, persistor, eventProducer)
        }
        case PrintPosition() => {
          ctx.log.info(s"Current position: ${state.position}")
          Behaviors.same
        }
        case PersistState() => {
          ctx.log.info(s"Persisting current state: ${state.position}")
          persistor ! PlayerPersistor.Persist(state)
          Behaviors.same
        }
      }
    })
  }
}
