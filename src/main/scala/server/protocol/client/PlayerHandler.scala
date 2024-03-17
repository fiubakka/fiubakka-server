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
import protobuf.client.init.player_login.PBPlayerLogin
import protobuf.client.init.player_register.PBPlayerRegister
import protobuf.client.inventory.update_equipment.PBPlayerUpdateEquipment
import protobuf.client.map.change_map.PBPlayerChangeMap
import protobuf.client.metadata.PBClientMetadata
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.server.chat.message.{PBPlayerMessage => PBPlayerMessageServer}
import protobuf.server.init.player_init.PBPlayerEquipment
import protobuf.server.init.player_init.PBPlayerInitErrorCode
import protobuf.server.init.player_init.PBPlayerInitSuccess
import protobuf.server.init.player_init.PBPlayerInitialState
import protobuf.server.init.player_init.PBPlayerPosition
import protobuf.server.map.change_map_ready.PBPlayerChangeMapReady
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.metadata.PBServerMetadata
import protobuf.server.state.game_entity_state.PBGameEntityEquipment
import protobuf.server.state.game_entity_state.PBGameEntityPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.state.game_entity_state.PBGameEntityVelocity
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.entities.InitData
import server.domain.entities.Player
import server.domain.structs.init.InitInfo
import server.domain.structs.init.LoginInfo
import server.domain.structs.init.RegisterInfo
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.infra.repository.PlayerRepository
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
  final case class InitSuccess(initInfo: InitInfo) extends Command
  final case class InitFailure(errorCode: PBPlayerInitErrorCode) extends Command
  final case class Move(velocity: Velocity, position: Position) extends Command
  final case class AddMessage(msg: String) extends Command
  final case class ChangeMap(newMapId: Int) extends Command
  final case class UpdateEquipment(equipment: Equipment) extends Command

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

        initBehaviour(conQueue, playerResponseMapper)
      }
    }
  }

  private def initBehaviour(
      conQueue: SourceQueueWithComplete[GeneratedMessage],
      playerResponseMapper: ActorRef[Player.ReplyCommand]
  ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Init(initInfo) => {
          ctx.log.info(s"Init message received from ${initInfo.playerName}")

          initInfo match {
            case LoginInfo(playerName, password) =>
              PlayerRepository
                .validate(playerName, password)
                .map {
                  case true =>
                    ctx.self ! InitSuccess(initInfo)
                  case false =>
                    ctx.self ! InitFailure(
                      PBPlayerInitErrorCode.INVALID_PLAYER_CREDENTIALS
                    )
                }
                .recover {
                  case _ => // TODO can we log this error? We cant use the ctx.log
                    ctx.self ! InitFailure(PBPlayerInitErrorCode.UNKNOWN)
                }

            case RegisterInfo(playerName, password, _) =>
              PlayerRepository
                .create(playerName, password)
                .map { _ =>
                  ctx.self ! InitSuccess(initInfo)
                }
                .recover { case err =>
                  println("LA PUTA MADRE FALLE")
                  println(err)
                  ctx.self ! InitFailure(PBPlayerInitErrorCode.UNKNOWN)
                }
          }

          Behaviors.same
        }

        case InitSuccess(initInfo) => {
          val player = Sharding().entityRefFor(
            Player.TypeKey,
            initInfo.playerName
          )

          player ! Player.Init(
            InitData(
              playerResponseMapper,
              initInfo.getInitialEquipment()
            )
          ) // Forces the Player to start the first time and syncs the handler

          Behaviors.same // Now we wait for Player.Ready message from the Player
        }

        case InitFailure(errorCode) => {
          ctx.log.info(
            s"Init failure message received when initializing connection to player. Error code $errorCode"
          )
          Behaviors.same
        }

        case PlayerReplyCommand(cmd) => {
          cmd match {
            case Player.Ready(initialState) => {
              val player = Sharding().entityRefFor(
                Player.TypeKey,
                initialState.playerName
              )
              val message = PBPlayerInitSuccess.of(
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
                  ),
                  initialState.mapId
                )
              )
              conQueue.offer(message)
              runningBehaviour(State(player, conQueue, playerResponseMapper))
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

        case ChangeMap(newMapId) => {
          state.player ! Player.ChangeMap(newMapId)
          Behaviors.same
        }

        case UpdateEquipment(equipment) => {
          state.player ! Player.UpdateEquipment(equipment)
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

            case Player.ChangeMapReady(newMapId) => {
              val message = PBPlayerChangeMapReady.of(newMapId)
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
    case PBPlayerLogin(playerName, password, _) =>
      Init(InitInfo.fromLoginInfo(playerName, password))
    case PBPlayerRegister(playerName, password, equipment, _) =>
      Init(
        InitInfo.fromRegisterInfo(
          playerName,
          password,
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
    case PBPlayerMovement(velocity, position, _) =>
      Move(Velocity(velocity.x, velocity.y), Position(position.x, position.y))
    case PBPlayerMessageClient(msg, _)  => AddMessage(msg)
    case PBPlayerChangeMap(newMapId, _) => ChangeMap(newMapId)
    case PBPlayerUpdateEquipment(
          hat,
          hair,
          eyes,
          glasses,
          facialHair,
          outfit,
          body,
          _
        ) =>
      UpdateEquipment(
        Equipment(
          hat,
          hair,
          eyes,
          glasses,
          facialHair,
          outfit,
          body
        )
      )
  }
}
