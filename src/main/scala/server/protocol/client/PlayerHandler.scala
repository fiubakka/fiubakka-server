package server.protocol.client

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import protobuf.client.chat.message.{PBPlayerMessage => PBPlayerMessageClient}
import protobuf.client.init.player_init.PBPlayerInit
import protobuf.client.metadata.PBClientMetadata
import protobuf.client.movement.player_velocity.PBPlayerVelocity
import protobuf.dummy.PBDummy
import protobuf.server.chat.message.{PBPlayerMessage => PBPlayerMessageServer}
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.metadata.PBServerMetadata
import protobuf.server.position.player_position.PBPlayerPosition
import protobuf.server.state.game_entity_state.PBGameEntityPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.state.game_entity_state.PBGameEntityVelocity
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.Sharding
import server.domain.entities.Player
import server.domain.structs.GameEntityState
import server.protocol.flows.InMessageFlow
import server.protocol.flows.server.protocol.flows.OutMessageFlow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PlayerHandler {
  sealed trait Command extends CborSerializable
  final case class ConnectionClosed() extends Command

  final case class Init(playerName: String) extends Command
  final case class StartMoving(x: Float, y: Float) extends Command
  final case class StopMoving() extends Command
  final case class Move(x: Float, y: Float) extends Command
  final case class AddMessage(msg: String) extends Command

  final case class MoveReply(x: Float, y: Float, velX: Float, velY: Float)
      extends Command
  final case class NotifyEntityStateUpdate(
      entityId: String,
      newEntityState: GameEntityState
  ) extends Command
  final case class NotifyMessageReceived(
      entityId: String,
      msg: String
  ) extends Command

  def apply(connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val mat = Materializer(ctx)

        val (conQueue, conSource) = Source
          .queue[GeneratedMessage](256, OverflowStrategy.backpressure)
          .preMaterialize()

        connection.handleWith(clientStreamHandler(ctx, conSource))

        Behaviors.receiveMessage {
          case Init(playerName) => {
            ctx.log.info(s"Init message received from $playerName")

            val player = Sharding().entityRefFor(
              Player.TypeKey,
              s"Player-$playerName"
            )
            player ! Player.Start(ctx.self)

            Behaviors.receiveMessage {
              case ConnectionClosed() => {
                ctx.log.info("Closing connection!")
                player ! Player.Stop()
                Behaviors.stopped
              }

              case StartMoving(x, y) => {
                ctx.log.info(s"StartMoving message received $x, $y!")
                timers.startTimerAtFixedRate("move", Move(x, y), 16666.micro)
                Behaviors.same
              }

              case StopMoving() => {
                ctx.log.info("StopMoving message received!")
                timers.cancel("move")
                Behaviors.same
              }

              case Move(x, y) => {
                player ! Player.Move(x, y, ctx.self)
                Behaviors.same
              }

              case MoveReply(x, y, velX, velY) => {
                conQueue.offer(PBPlayerPosition.of(x, y, velX, velY))
                Behaviors.same
              }

              case NotifyEntityStateUpdate(entityId, newEntityState) => {
                val message = PBGameEntityState
                  .of(
                    entityId,
                    PBGameEntityPosition
                      .of(newEntityState.position.x, newEntityState.position.y),
                    PBGameEntityVelocity
                      .of(
                        newEntityState.velocity.velX,
                        newEntityState.velocity.velY
                      )
                  )
                conQueue.offer(message)
                Behaviors.same
              }

              case AddMessage(msg) => {
                player ! Player.AddMessage(msg)
                Behaviors.same
              }

              case NotifyMessageReceived(entityId, msg) => {
                val message = PBPlayerMessageServer.of(entityId, msg)
                conQueue.offer(message)
                Behaviors.same
              }

              case _ => {
                Behaviors.same
              }
            }
          }

          case _ => {
            Behaviors.same
          }
        }
      }
    }
  }

  private def clientStreamHandler(
      ctx: ActorContext[Command],
      conSource: Source[GeneratedMessage, NotUsed]
  ) = {
    Flow[ByteString]
      .via(
        InMessageFlow(PBClientMetadata, ProtocolMessageMap.clientMessageMap)
      )
      .map(commandFromClientMessage)
      .map { msg =>
        ctx.self ! msg
        // Trick the Scala compiler, we don't need any value here but null breaks Akka Stream
        PBDummy()
      }
      .filter(_ => false) // Drop all
      .merge(conSource)
      .via {
        OutMessageFlow(
          (length: Int, `type`: GeneratedEnum) =>
            PBServerMetadata(length, `type`.asInstanceOf[PBServerMessageType]),
          ProtocolMessageMap.serverMessageMap
        )
      }
      .watchTermination() { (_, done) =>
        done.onComplete(_ => {
          ctx.self ! ConnectionClosed()
        })
      }
  }

  private val commandFromClientMessage
      : PartialFunction[GeneratedMessage, Command] = {
    case PBPlayerInit(playerName, _)   => Init(playerName)
    case PBPlayerVelocity(0, 0, _)     => StopMoving()
    case PBPlayerVelocity(x, y, _)     => StartMoving(x, y)
    case PBPlayerMessageClient(msg, _) => AddMessage(msg)
  }
}
