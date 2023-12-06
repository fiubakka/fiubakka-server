package server.domain.entities

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.serialization.jackson.CborSerializable
import akka.util.Timeout
import server.Sharding
import server.domain.structs.PlayerPosition
import server.domain.structs.PlayerState
import server.infra.PlayerPersistor
import server.protocol.GameEventConsumer
import server.protocol.GameEventProducer
import server.protocol.client.PlayerHandler

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object Player {

  sealed trait Command extends CborSerializable
  // This message is a dummy for initialization purposes in Player Handler
  final case class Start() extends Command
  final case class Stop() extends Command
  final case class InitState(
      initialState: PlayerState
  ) extends Command
  final case class Move(
      velX: Float,
      velY: Float,
      replyTo: ActorRef[PlayerHandler.MoveReply]
  ) extends Command
  final case class PrintPosition() extends Command
  final case class PersistState() extends Command

  val TypeKey = EntityTypeKey[Command]("Player")

  def apply(
      entityId: String
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val askTimeout = Timeout(5.seconds)

        timers.startTimerAtFixedRate(
          "persist",
          PersistState(),
          30.seconds
        )
        ctx.log.info(s"Starting player $entityId")

        val persistor = Sharding().entityRefFor(
          PlayerPersistor.TypeKey,
          entityId
        )

        ctx.spawn(
          GameEventConsumer(entityId, ctx.self),
          s"GameEventConsumer-$entityId"
        )

        val eventProducer = ctx.spawn(
          GameEventProducer(entityId),
          s"GameEventProducer-$entityId"
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
      eventProducer: ActorRef[GameEventProducer.Command]
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
      eventProducer: ActorRef[GameEventProducer.Command]
  ): Behavior[Command] = {
    Behaviors.receive((ctx, msg) => {
      msg match {
        case Move(velX, velY, replyTo) => {
          val newState = state.copy(
            position = PlayerPosition(
              state.position.x + (velX * 5),
              state.position.y + (velY * 5)
            )
          )
          replyTo ! PlayerHandler.MoveReply(
            newState.position.x,
            newState.position.y
          )
          eventProducer ! GameEventProducer.PlayerStateUpdate(newState)
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
        case Stop() => {
          ctx.log.info(s"Stopping player ${ctx.self.path.name}")
          Behaviors.stopped
        }
        case _ => {
          Behaviors.same // TODO throw error or something, it should not receive these message again
        }
      }
    })
  }
}
