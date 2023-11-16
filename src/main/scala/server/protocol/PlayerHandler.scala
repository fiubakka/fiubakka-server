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

object PlayerHandler {
  sealed trait Command
  final case class GetIPInfo(replyTo: ActorRef[Response]) extends Command

  final case class Response(ip: String, port: Int)

  def apply()(implicit system: ActorSystem[GameServer.Command]): Behavior[Command] = {
    Behaviors.setup(ctx => {
      // val echo = Flow[ByteString]
      //   .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      //   .map(_.utf8String)
      //   .map(_ + ctx.self + "!!!\n")
      //   .map(ByteString(_))

      // connection.handleWith(echo)
      
      Behaviors.receiveMessage {
        case GetIPInfo(replyTo) => {
          replyTo ! Response("Hello, world!", 123)
          Behaviors.same
        }
      }
    })
  }
}
