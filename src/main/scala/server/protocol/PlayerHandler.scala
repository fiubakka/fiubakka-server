package server.protocol

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import server.domain.entities.Player
import akka.stream.scaladsl.Tcp
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import server.GameServer
import akka.actor.typed.ActorRef
import scala.util.Success
import scala.util.Failure
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

object PlayerHandler {
  sealed trait Command
  final case class GetIPInfo(replyTo: ActorRef[GetIPInfoResponse])
      extends Command
  final case class ServerBinding(
      ip: String,
      port: Int,
      replyTo: ActorRef[GetIPInfoResponse]
  ) extends Command

  final case class GetIPInfoResponse(ip: String, port: Int)

  def apply()(implicit
      system: ActorSystem[GameServer.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val connectionListener = Tcp(system).bind("localhost", 0)
      val serverBinding = connectionListener
        .toMat(Sink.foreach { connection =>
          val clientResponse = Flow[ByteString]
            .via(
              Framing.delimiter(ByteString("\n"), 256, allowTruncation = true)
            )
            .map(_.utf8String)
            .map(_ + "!!!\n")
            .map(ByteString(_))

          connection.handleWith(clientResponse)
        })(Keep.left)
        .run()

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
      }
    })
  }
}
