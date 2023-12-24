package server.protocol.client

import akka.NotUsed
import akka.actor.typed.ActorRef
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
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.dummy.PBDummy
import protobuf.server.chat.message.{PBPlayerMessage => PBPlayerMessageServer}
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.metadata.PBServerMetadata
import protobuf.server.state.game_entity_state.PBGameEntityPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.state.game_entity_state.PBGameEntityVelocity
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.entities.Player
import server.domain.entities.Player.ReplyStop
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.protocol.flows.InMessageFlow
import server.protocol.flows.server.protocol.flows.OutMessageFlow
import server.sharding.Sharding

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PlayerHandler {
  sealed trait Command extends CborSerializable
  final case class ConnectionClosed() extends Command

  final case class SendHeartbeat() extends Command
  final case class Init(playerName: String) extends Command
  final case class Move(velocity: Velocity, position: Position) extends Command
  final case class AddMessage(msg: String) extends Command

  final case class PlayerReplyCommand(cmd: Player.ReplyCommand) extends Command

  def apply(connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val mat = Materializer(ctx)

        val (conQueue, conSource) = Source
          .queue[GeneratedMessage](64000, OverflowStrategy.dropHead)
          .preMaterialize()

        connection.handleWith(clientStreamHandler(ctx, conSource))

        val playerResponseMapper: ActorRef[Player.ReplyCommand] =
          ctx.messageAdapter(rsp => PlayerReplyCommand(rsp))

        timers.startTimerWithFixedDelay(
          "sendHeartbeat",
          SendHeartbeat(),
          2.seconds
        )

        Behaviors.receiveMessage {
          case Init(playerName) => {
            ctx.log.info(s"Init message received from $playerName")

            val player = Sharding().entityRefFor(
              Player.TypeKey,
              playerName
            )

            player ! Player.Heartbeat(playerResponseMapper) // Forces the Player to start

            Behaviors.receiveMessage {
              case ConnectionClosed() => {
                ctx.log.info("Closing connection!")
                player ! Player.Stop()
                Behaviors.stopped
              }

              case Move(velocity, position) => {
                player ! Player.Move(velocity, position)
                Behaviors.same
              }

              case AddMessage(msg) => {
                player ! Player.AddMessage(msg)
                Behaviors.same
              }

              case PlayerReplyCommand(cmd) => {
                cmd match {
                  case Player.NotifyEntityStateUpdate(
                        entityId,
                        newEntityState
                      ) => {
                    val message = PBGameEntityState
                      .of(
                        entityId,
                        PBGameEntityPosition
                          .of(
                            newEntityState.position.x,
                            newEntityState.position.y
                          ),
                        PBGameEntityVelocity
                          .of(
                            newEntityState.velocity.x,
                            newEntityState.velocity.y
                          )
                      )
                    conQueue.offer(message)
                    Behaviors.same
                  }

                  case Player.NotifyMessageReceived(entityId, msg) => {
                    val message = PBPlayerMessageServer.of(entityId, msg)
                    conQueue.offer(message)
                    Behaviors.same
                  }

                  case ReplyStop() => {
                    ctx.log.info("Player stopped!")
                    Behaviors.stopped
                  }
                }
              }

              case SendHeartbeat() => {
                player ! Player.Heartbeat(playerResponseMapper)
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
      .throttle(60, 1.second) // 60hz tick rate
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
    case PBPlayerInit(playerName, _) => Init(playerName)
    case PBPlayerMovement(velocity, position, _) =>
      Move(Velocity(velocity.x, velocity.y), Position(position.x, position.y))
    case PBPlayerMessageClient(msg, _) => AddMessage(msg)
  }
}
