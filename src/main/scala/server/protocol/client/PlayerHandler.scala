package server.protocol.client

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import protobuf.client.chat.message.{PBPlayerMessage => PBPlayerMessageClient}
import protobuf.client.init.player_init.PBPlayerInit
import protobuf.client.metadata.PBClientMetadata
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.server.chat.message.{PBPlayerMessage => PBPlayerMessageServer}
import protobuf.server.init.player_init_ready.PBPlayerEquipment
import protobuf.server.init.player_init_ready.PBPlayerInitReady
import protobuf.server.init.player_init_ready.PBPlayerInitialState
import protobuf.server.init.player_init_ready.PBPlayerPosition
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.metadata.PBServerMetadata
import protobuf.server.state.game_entity_state.PBGameEntityEquipment
import protobuf.server.state.game_entity_state.PBGameEntityPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.state.game_entity_state.PBGameEntityVelocity
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.entities.Player
import server.domain.structs.init.InitInfo
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.protocol.flows.InMessageFlow
import server.protocol.flows.server.protocol.flows.OutMessageFlow
import server.sharding.Sharding

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PlayerHandler {
  final case class State(
      player: EntityRef[Player.Command],
      conQueue: SourceQueueWithComplete[GeneratedMessage],
      playerResponseMapper: ActorRef[Player.ReplyCommand]
  )

  sealed trait Command extends CborSerializable
  final case class ConnectionClosed() extends Command

  final case class SendHeartbeat() extends Command
  final case class Init(initInfo: InitInfo) extends Command
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
          case Init(initInfo) => {
            ctx.log.info(s"Init message received from ${initInfo.playerName}")

            val player = Sharding().entityRefFor(
              Player.TypeKey,
              initInfo.playerName
            )

            player ! Player.Init(
              playerResponseMapper
            ) // Forces the Player to start the first time and syncs the handler

            initBehaviour(State(player, conQueue, playerResponseMapper))
          }

          case _ => Behaviors.same
        }
      }
    }
  }

  private def initBehaviour(state: State): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Init(initInfo) => {
          ctx.log.info(
            s"Another init message received from ${initInfo.playerName}"
          )
          state.player ! Player.Init(
            state.playerResponseMapper
          ) // Renotify the Player to start, we should eventually receive Player.Ready message
          Behaviors.same
        }

        case PlayerReplyCommand(cmd) => {
          cmd match {
            case Player.Ready(initialState) => {
              val message = PBPlayerInitReady.of(
                PBPlayerInitialState.of(
                  PBPlayerPosition.of(
                    initialState.position.x,
                    initialState.position.y
                  ),
                  PBPlayerEquipment.of(
                    initialState.equipment.hat,
                    initialState.equipment.hair,
                    initialState.equipment.eyes,
                    initialState.equipment.glasses,
                    initialState.equipment.facialHair,
                    initialState.equipment.body,
                    initialState.equipment.outfit
                  )
                )
              )
              state.conQueue.offer(message)
              runningBehaviour(state)
            }

            case _ => Behaviors.same
          }
        }

        case _ => {
          Behaviors.same
        }
      }
    }
  }

  private def runningBehaviour(state: State): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ConnectionClosed() => {
          ctx.log.info("Closing connection!")
          state.player ! Player.Stop()
          Behaviors.stopped
        }

        case Move(velocity, position) => {
          state.player ! Player.Move(velocity, position)
          Behaviors.same
        }

        case AddMessage(msg) => {
          state.player ! Player.AddMessage(msg)
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
                    ),
                  PBGameEntityEquipment.of(
                    newEntityState.equipment.hat,
                    newEntityState.equipment.hair,
                    newEntityState.equipment.eyes,
                    newEntityState.equipment.glasses,
                    newEntityState.equipment.facialHair,
                    newEntityState.equipment.body,
                    newEntityState.equipment.outfit
                  )
                )
              state.conQueue.offer(message)
              Behaviors.same
            }

            case Player.NotifyMessageReceived(entityId, msg) => {
              val message = PBPlayerMessageServer.of(entityId, msg)
              state.conQueue.offer(message)
              Behaviors.same
            }

            case Player.ReplyStop() => {
              ctx.log.info("Player stopped!")
              Behaviors.stopped
            }

            case _ => Behaviors.same
          }
        }

        case SendHeartbeat() => {
          state.player ! Player.Heartbeat()
          Behaviors.same
        }

        case _ => {
          Behaviors.same
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
      .via(
        Flow.fromSinkAndSourceCoupled(
          Sink.foreach { msg =>
            ctx.self ! msg
          },
          conSource.via(
            OutMessageFlow(
              (length: Int, `type`: GeneratedEnum) =>
                PBServerMetadata(
                  length,
                  `type`.asInstanceOf[PBServerMessageType]
                ),
              ProtocolMessageMap.serverMessageMap
            )
          )
        )
      )
      .watchTermination() { (_, done) =>
        done.onComplete(_ => {
          ctx.self ! ConnectionClosed()
        })
      }
  }

  private val commandFromClientMessage
      : PartialFunction[GeneratedMessage, Command] = {
    case PBPlayerInit(playerName, Some(equipment), _) =>
      Init(
        InitInfo.fromRegisterInfo(
          playerName,
          Equipment(
            equipment.hat,
            equipment.hair,
            equipment.eyes,
            equipment.glasses,
            equipment.facialHair,
            equipment.body,
            equipment.outfit
          )
        )
      )
    case PBPlayerInit(playerName, None, _) =>
      Init(InitInfo.fromLoginInfo(playerName))
    case PBPlayerMovement(velocity, position, _) =>
      Move(Velocity(velocity.x, velocity.y), Position(position.x, position.y))
    case PBPlayerMessageClient(msg, _) => AddMessage(msg)
  }
}
