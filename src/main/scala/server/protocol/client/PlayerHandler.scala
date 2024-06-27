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
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes.FlowWithInstrumented
import protobuf.client.chat.message.PBPlayerMessage as PBPlayerMessageClient
import protobuf.client.init.player_login.PBPlayerLogin
import protobuf.client.init.player_register.PBPlayerRegister
import protobuf.client.inventory.update_equipment.PBPlayerUpdateEquipment
import protobuf.client.map.change_map.PBPlayerChangeMap
import protobuf.client.metadata.PBClientMetadata
import protobuf.client.movement.player_movement.PBPlayerMovement
import protobuf.client.truco.ack_play.PBTrucoAckPlay
import protobuf.client.truco.disconnect.PBTrucoDisconnect
import protobuf.client.truco.match_challenge.PBTrucoMatchChallenge
import protobuf.client.truco.match_challenge_reply.PBTrucoMatchChallengeReply
import protobuf.client.truco.match_challenge_reply.PBTrucoMatchChallengeReplyEnum
import protobuf.client.truco.play.PBTrucoPlay as PBClientTrucoPlay
import protobuf.client.truco.play.PBTrucoPlayType.CARD
import protobuf.client.truco.play.PBTrucoPlayType.SHOUT
import protobuf.client.truco.play.PBTrucoShout as PBClientTrucoShout
import protobuf.server.chat.message.PBPlayerMessage as PBPlayerMessageServer
import protobuf.server.init.player_init.PBPlayerEquipment
import protobuf.server.init.player_init.PBPlayerInitError
import protobuf.server.init.player_init.PBPlayerInitErrorCode
import protobuf.server.init.player_init.PBPlayerInitSuccess
import protobuf.server.init.player_init.PBPlayerInitialState
import protobuf.server.init.player_init.PBPlayerPosition
import protobuf.server.map.change_map_ready.PBPlayerChangeMapReady
import protobuf.server.metadata.PBServerMessageType
import protobuf.server.metadata.PBServerMetadata
import protobuf.server.state.game_entity_disconnect.PBGameEntityDisconnect
import protobuf.server.state.game_entity_state.PBGameEntityEquipment
import protobuf.server.state.game_entity_state.PBGameEntityPosition
import protobuf.server.state.game_entity_state.PBGameEntityState
import protobuf.server.state.game_entity_state.PBGameEntityVelocity
import protobuf.server.truco.allow_play.PBTrucoAllowPlay
import protobuf.server.truco.match_challenge_denied.PBTrucoMatchChallengeDenied
import protobuf.server.truco.match_challenge_request.PBTrucoMatchChallengeRequest
import protobuf.server.truco.play.PBTrucoCard
import protobuf.server.truco.play.PBTrucoCardSuit
import protobuf.server.truco.play.PBTrucoNextPlay
import protobuf.server.truco.play.PBTrucoPlay as PBServerTrucoPlay
import protobuf.server.truco.play.PBTrucoPlayType
import protobuf.server.truco.play.PBTrucoPoints
import protobuf.server.truco.play.PBTrucoShout as PBServerTrucoShout
import protobuf.server.truco.player_disconnected.PBTrucoPlayerDisconnected
import scalapb.GeneratedEnum
import scalapb.GeneratedMessage
import server.domain.entities.player.Player
import server.domain.structs.init.InitInfo
import server.domain.structs.init.LoginInfo
import server.domain.structs.init.RegisterInfo
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.domain.structs.truco.TrucoCardPlay
import server.domain.structs.truco.TrucoMatchChallengeReplyEnum
import server.domain.structs.truco.TrucoPlay
import server.domain.structs.truco.TrucoShoutEnum
import server.domain.structs.truco.TrucoShoutPlay
import server.domain.truco.cards.CardSuit
import server.infra.repository.PlayerRepository
import server.protocol.flows.InMessageFlow
import server.protocol.flows.server.protocol.flows.OutMessageFlow
import server.protocol.truco.TrucoPlayType
import server.sharding.Sharding

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

object PlayerHandler {
  final case class State(
      player: EntityRef[Player.Command],
      conQueue: SourceQueueWithComplete[GeneratedMessage]
  )

  sealed trait Command extends CborSerializable
  private type CommandOrPlayerReply = Command | Player.ReplyCommand
  final case class ConnectionClosed() extends Command

  final case class SendHeartbeat() extends Command
  final case class Init(initInfo: InitInfo) extends Command
  final case class InitSuccess(initInfo: InitInfo) extends Command
  final case class InitFailure(errorCode: PBPlayerInitErrorCode) extends Command
  final case class Move(velocity: Velocity, position: Position) extends Command
  final case class AddMessage(msg: String) extends Command
  final case class ChangeMap(newMapId: Int) extends Command
  final case class UpdateEquipment(equipment: Equipment) extends Command
  final case class TrucoMatchChallenge(opponentUsername: String) extends Command
  final case class TrucoMatchChallengeReply(
      opponentUsername: String,
      status: TrucoMatchChallengeReplyEnum
  ) extends Command
  final case class TrucoMatchPlay(playId: Int, play: TrucoPlay) extends Command
  final case class TrucoMatchAckPlay(playId: Int) extends Command
  final case class TrucoDisconnect() extends Command

  def apply(
      connection: Tcp.IncomingConnection
  ): Behavior[CommandOrPlayerReply] = {
    Behaviors.setup { ctx =>
      implicit val mat = Materializer(ctx)

      val (conQueue, conSource) = Source
        .queue[GeneratedMessage](64000, OverflowStrategy.dropHead)
        .preMaterialize()

      connection.handleWith(clientStreamHandler(ctx, conSource))

      initBehavior(conQueue)
    }
  }

  private def initBehavior(
      conQueue: SourceQueueWithComplete[GeneratedMessage]
  ): Behavior[CommandOrPlayerReply] = {
    Behaviors.withTimers { timers =>
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
                  .recover { case err =>
                    Console.err.println(
                      "Unkown error validating player credentials: " + err
                    )
                    ctx.self ! InitFailure(PBPlayerInitErrorCode.UNKNOWN)
                  }

              case RegisterInfo(playerName, password, _) =>
                PlayerRepository
                  .create(playerName, password)
                  .map { _ =>
                    ctx.self ! InitSuccess(initInfo)
                  }
                  .recover {
                    case PlayerRepository.UserAlreadyExistsException(_) =>
                      ctx.self ! InitFailure(
                        PBPlayerInitErrorCode.PLAYER_ALREADY_EXISTS
                      )
                    case err =>
                      Console.err.println(
                        "Unkown error registering player: " + err
                      )
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
              Player.InitData(
                ctx.self,
                initInfo.getInitialEquipment()
              )
            ) // Forces the Player to start the first time and syncs the handler

            Behaviors.same // Now we wait for Player.Ready message from the Player
          }

          case InitFailure(errorCode) => {
            ctx.log.info(
              s"Init failure message received when initializing connection to player. Error code $errorCode"
            )
            val message = PBPlayerInitError.of(errorCode.name)
            conQueue.offer(message)
            Behaviors.same
          }

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
            timers.startTimerWithFixedDelay(
              "sendHeartbeat",
              SendHeartbeat(),
              2.seconds
            )
            runningBehavior(State(player, conQueue))
          }

          case ConnectionClosed() => {
            Behaviors.stopped
          }

          case _ => {
            Behaviors.same
          }
        }
      }
    }
  }

  private def runningBehavior(state: State): Behavior[CommandOrPlayerReply] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ConnectionClosed() => {
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

        case TrucoMatchChallenge(opponentUsername) => {
          state.player ! Player.BeginTrucoMatch(opponentUsername)
          Behaviors.same
        }

        case TrucoMatchChallengeReply(opponentUsername, status) => {
          state.player ! Player.ReplyBeginTrucoMatch(
            opponentUsername,
            status
          )
          Behaviors.same
        }

        case TrucoMatchPlay(playId, play) => {
          state.player ! Player.TrucoMatchPlay(playId, play)
          Behaviors.same
        }

        case TrucoMatchAckPlay(playId) => {
          state.player ! Player.TrucoMatchAckPlay(playId)
          Behaviors.same
        }

        case TrucoDisconnect() => {
          state.player ! Player.TrucoDisconnect()
          Behaviors.same
        }

        case Player.NotifyAskBeginTrucoMatch(opponentUsername) => {
          val message = PBTrucoMatchChallengeRequest.of(opponentUsername)
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyBeginTrucoMatchDenied(
              opponentUsername
            ) => {
          val message = PBTrucoMatchChallengeDenied.of(opponentUsername)
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyTrucoPlayStateInfo(playState) => {
          val message = PBServerTrucoPlay(
            playId = playState.playId,
            playType = playState.playType match {
              case TrucoPlayType.Card =>
                PBTrucoPlayType.CARD
              case TrucoPlayType.Shout =>
                PBTrucoPlayType.SHOUT
              case TrucoPlayType.Update =>
                PBTrucoPlayType.UPDATE
            },
            playerCards = playState.playerCards.map { c =>
              PBTrucoCard.of(
                cardId = c.cardId,
                suit = c.card.suit match {
                  case CardSuit.Clubs  => PBTrucoCardSuit.CLUBS
                  case CardSuit.Swords => PBTrucoCardSuit.SWORDS
                  case CardSuit.Cups   => PBTrucoCardSuit.CUPS
                  case CardSuit.Coins  => PBTrucoCardSuit.COINS
                },
                number = c.numberToInt()
              )
            },
            opponentCardAmount = playState.opponentCardAmount,
            firstPlayerPoints = PBTrucoPoints.of(
              playState.firstPlayerPoints.playerName,
              playState.firstPlayerPoints.points
            ),
            secondPlayerPoints = PBTrucoPoints.of(
              playState.secondPlayerPoints.playerName,
              playState.secondPlayerPoints.points
            ),
            isGameOver = playState.isGameOver,
            isMatchOver = playState.isMatchOver,
            card = playState.card.map { c =>
              PBTrucoCard.of(
                cardId = c.cardId, // Not actually used, contains a dummy value
                suit = c.card.suit match {
                  case CardSuit.Clubs  => PBTrucoCardSuit.CLUBS
                  case CardSuit.Swords => PBTrucoCardSuit.SWORDS
                  case CardSuit.Cups   => PBTrucoCardSuit.CUPS
                  case CardSuit.Coins  => PBTrucoCardSuit.COINS
                },
                number = c.numberToInt()
              )
            },
            shout = playState.shout.map {
              case TrucoShoutEnum.Mazo       => PBServerTrucoShout.MAZO
              case TrucoShoutEnum.Envido     => PBServerTrucoShout.ENVIDO
              case TrucoShoutEnum.RealEnvido => PBServerTrucoShout.REAL_ENVIDO
              case TrucoShoutEnum.FaltaEnvido =>
                PBServerTrucoShout.FALTA_ENVIDO
              case TrucoShoutEnum.EnvidoQuiero =>
                PBServerTrucoShout.ENVIDO_QUIERO
              case TrucoShoutEnum.EnvidoNoQuiero =>
                PBServerTrucoShout.ENVIDO_NO_QUIERO
              case TrucoShoutEnum.Truco      => PBServerTrucoShout.TRUCO
              case TrucoShoutEnum.Retruco    => PBServerTrucoShout.RETRUCO
              case TrucoShoutEnum.Valecuatro => PBServerTrucoShout.VALE_CUATRO
              case TrucoShoutEnum.TrucoQuiero =>
                PBServerTrucoShout.TRUCO_QUIERO
              case TrucoShoutEnum.TrucoNoQuiero =>
                PBServerTrucoShout.TRUCO_NO_QUIERO
            },
            nextPlayInfo = playState.nextPlayInfo.map { np =>
              PBTrucoNextPlay.of(
                nextPlayer = np.nextPlayer,
                isPlayCardAvailable = np.isPlayCardAvailable,
                availableShouts = np.availableShouts.map {
                  case TrucoShoutEnum.Mazo   => PBServerTrucoShout.MAZO
                  case TrucoShoutEnum.Envido => PBServerTrucoShout.ENVIDO
                  case TrucoShoutEnum.RealEnvido =>
                    PBServerTrucoShout.REAL_ENVIDO
                  case TrucoShoutEnum.FaltaEnvido =>
                    PBServerTrucoShout.FALTA_ENVIDO
                  case TrucoShoutEnum.EnvidoQuiero =>
                    PBServerTrucoShout.ENVIDO_QUIERO
                  case TrucoShoutEnum.EnvidoNoQuiero =>
                    PBServerTrucoShout.ENVIDO_NO_QUIERO
                  case TrucoShoutEnum.Truco   => PBServerTrucoShout.TRUCO
                  case TrucoShoutEnum.Retruco => PBServerTrucoShout.RETRUCO
                  case TrucoShoutEnum.Valecuatro =>
                    PBServerTrucoShout.VALE_CUATRO
                  case TrucoShoutEnum.TrucoQuiero =>
                    PBServerTrucoShout.TRUCO_QUIERO
                  case TrucoShoutEnum.TrucoNoQuiero =>
                    PBServerTrucoShout.TRUCO_NO_QUIERO
                }
              )
            }
          )
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyTrucoAllowPlay(playId) => {
          val message = PBTrucoAllowPlay.of(playId)
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyTrucoPlayerDisconnected(opponentUsername) => {
          val message = PBTrucoPlayerDisconnected.of(opponentUsername)
          state.conQueue.offer(message)
          Behaviors.same
        }

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
              ),
              metrics = None // TODO send actual metrics
            )
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyMessageReceived(entityId, msg) => {
          val message = PBPlayerMessageServer.of(entityId, msg)
          state.conQueue.offer(message)
          Behaviors.same
        }

        case Player.NotifyEntityDisconnect(entityId) => {
          val message = PBGameEntityDisconnect.of(entityId)
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

        case SendHeartbeat() => {
          state.player ! Player.Heartbeat(ctx.self)
          Behaviors.same
        }

        case _ => {
          Behaviors.same
        }
      }
    }
  }

  private def clientStreamHandler(
      ctx: ActorContext[CommandOrPlayerReply],
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
      .instrumentedPartial(
        name = "PlayerHandlerConnection",
        traceable = true,
        reportByName = true
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
    case PBTrucoMatchChallenge(opponentUsername, _) =>
      TrucoMatchChallenge(opponentUsername)
    case PBTrucoMatchChallengeReply(opponentUsername, status, _) =>
      TrucoMatchChallengeReply(
        opponentUsername,
        status match {
          case PBTrucoMatchChallengeReplyEnum.ACCEPTED =>
            TrucoMatchChallengeReplyEnum.Accepted
          case PBTrucoMatchChallengeReplyEnum.REJECTED =>
            TrucoMatchChallengeReplyEnum.Rejected
          case invalidValue => {
            Console.err.println(
              "Rejecting TrucoMatch because of invalid TrucoMatchChallengeReplyEnum: " + invalidValue
            )
            TrucoMatchChallengeReplyEnum.Rejected
          }
        }
      )
    case PBClientTrucoPlay(playId, playType, card, shout, _) =>
      TrucoMatchPlay(
        playId,
        playType match {
          case CARD => Left(TrucoCardPlay(card.get))
          case SHOUT =>
            Right(TrucoShoutPlay(shout.get match {
              case PBClientTrucoShout.MAZO         => TrucoShoutEnum.Mazo
              case PBClientTrucoShout.ENVIDO       => TrucoShoutEnum.Envido
              case PBClientTrucoShout.REAL_ENVIDO  => TrucoShoutEnum.RealEnvido
              case PBClientTrucoShout.FALTA_ENVIDO => TrucoShoutEnum.FaltaEnvido
              case PBClientTrucoShout.ENVIDO_QUIERO =>
                TrucoShoutEnum.EnvidoQuiero
              case PBClientTrucoShout.ENVIDO_NO_QUIERO =>
                TrucoShoutEnum.EnvidoNoQuiero
              case PBClientTrucoShout.TRUCO        => TrucoShoutEnum.Truco
              case PBClientTrucoShout.RETRUCO      => TrucoShoutEnum.Retruco
              case PBClientTrucoShout.VALE_CUATRO  => TrucoShoutEnum.Valecuatro
              case PBClientTrucoShout.TRUCO_QUIERO => TrucoShoutEnum.TrucoQuiero
              case PBClientTrucoShout.TRUCO_NO_QUIERO =>
                TrucoShoutEnum.TrucoNoQuiero
              case invalidShout =>
                throw new Exception("Invalid TrucoShoutEnum: " + invalidShout)
            }))
          case invalidPlayType =>
            throw new Exception("Invalid TrucoPlayType: " + invalidPlayType)
        }
      )
    case PBTrucoAckPlay(playId, _) => TrucoMatchAckPlay(playId)
    case PBTrucoDisconnect(_)      => TrucoDisconnect()
  }
}
