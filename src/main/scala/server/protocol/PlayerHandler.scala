package server.protocol

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import server.GameServer
import server.domain.entities.Player

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

object PlayerHandler {
  sealed trait Command
  final case class GetIPInfo(replyTo: ActorRef[GetIPInfoResponse])
      extends Command
  final case class ServerBinding(
      ip: String,
      port: Int,
      replyTo: ActorRef[GetIPInfoResponse]
  ) extends Command
  final case class ConnectionClosed() extends Command

  final case class GetIPInfoResponse(ip: String, port: Int)

  def apply()(implicit
      system: ActorSystem[GameServer.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val connectionListener = Tcp(system).bind("localhost", 0)
      val serverBinding = connectionListener
        .take(1)
        .to(Sink.foreach { connection =>
          val clientResponse = Flow[ByteString]
            .via(
              Framing.delimiter(ByteString("\n"), 256, allowTruncation = true)
            )
            .map(_.utf8String)
            .map(_ + "!!!\n")
            .map(ByteString(_))
            .watchTermination() { (_, done) =>
              done.onComplete(_ => ctx.self ! ConnectionClosed())
            }

          connection.handleWith(clientResponse)
        })
        .run()

      val player = ctx.spawn(Player(PersistenceId("foo", "bar")), "player")

      Behaviors.receiveMessage {
        case GetIPInfo(replyTo) => {
          ctx.pipeToSelf(serverBinding) {
            case Success(binding) =>
              ServerBinding(
                binding.localAddress.getHostString,
                binding.localAddress.getPort,
                replyTo
              )
            case Failure(_) => ServerBinding("", 0, replyTo)
          }
          Behaviors.same
        }

        case ServerBinding(ip, port, replyTo) => {
          replyTo ! GetIPInfoResponse(ip, port)
          Behaviors.same
        }

        case ConnectionClosed() => {
          ctx.log.info("Closing connection!")
          player ! Player.PrintPosition()
          player ! Player.Move(Player.Position(1, 1))
          player ! Player.PrintPosition()
          Behaviors.same
        }
      }
    })
  }
}
