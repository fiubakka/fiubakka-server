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

object PlayerHandler {
  sealed trait Command
  final case class Run() extends Command

  def apply(connection: Tcp.IncomingConnection)(implicit system: ActorSystem[GameServer.Command]): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val player = ctx.spawn(Player(Player.Position(1, 2)), "mainPlayer")

      val echo = Flow[ByteString]
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
        .map(_.utf8String)
        .map(_ + ctx.self + "!!!\n")
        .map(ByteString(_))

      connection.handleWith(echo)
      Behaviors.empty
    })
  }
}
