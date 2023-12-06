package server.protocol.client

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import server.Sharding
import server.domain.entities.Player

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import protobuf.common.metadata.PBMetadata
import protobuf.init.player_init.PBPlayerInit
import akka.actor.typed.scaladsl.ActorContext
import akka.NotUsed
import scalapb.GeneratedMessage

object PlayerHandler {
  sealed trait Command extends CborSerializable
  final case class ConnectionClosed() extends Command

  final case class Init(playerName: String) extends Command
  final case class StartMoving(x: Int, y: Int) extends Command
  final case class StopMoving() extends Command
  final case class Move(x: Int, y: Int) extends Command

  final case class MoveReply(x: Int, y: Int) extends Command

  def apply(connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val mat = Materializer(ctx)

        val (conQueue, conSource) = Source
          .queue[ByteString](256, OverflowStrategy.backpressure)
          .preMaterialize()

        connection.handleWith(clientStreamHandler(ctx, conSource))

        Behaviors.receiveMessage {
          case Init(playerName) => {
            ctx.log.info(s"Init message received from $playerName")
            val player = Sharding().entityRefFor(
              Player.TypeKey,
              s"Player-$playerName"
            )
            player ! Player.Start()

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

              case MoveReply(x, y) => {
                conQueue.offer(ByteString(s"POS $x $y\n"))
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

  private def clientStreamHandler(ctx: ActorContext[Command], conSource: Source[ByteString, NotUsed]) = {
    Flow[ByteString]
      .via(
        Framing.lengthField(
          fieldLength = 4,
          maximumFrameLength = 65535,
          byteOrder = java.nio.ByteOrder.BIG_ENDIAN
        )
      )
      .map { messageBytes =>
        try {
          val msgBytes = messageBytes.drop(4) // Ignore the length field from the frame, we don't need it
          val metadataSize = msgBytes.take(4).iterator.getInt(java.nio.ByteOrder.BIG_ENDIAN)
          val metadata = PBMetadata.parseFrom(msgBytes.drop(4).take(metadataSize).toArray)
          val (messageSize, messageType) = (metadata.length, metadata.`type`)
          Some(ClientProtocolMessageMap.messageMap(messageType).parseFrom(msgBytes.drop(4 + metadataSize).take(messageSize).toArray))
        } catch {
          case _: Throwable => None
        }
      }
      .collect { case Some(msg) => msg }
      .map(commandFromClientMessage)
      .map { msg =>
        ctx.self ! msg
        ByteString.empty
      }
      .merge(conSource)
      .watchTermination() { (_, done) =>
        done.onComplete(_ => {
          ctx.self ! ConnectionClosed()
        })
      }
  }

  private val commandFromClientMessage: PartialFunction[GeneratedMessage, Command] = {
    case PBPlayerInit(playerName, _) => Init(playerName)
  }
}
