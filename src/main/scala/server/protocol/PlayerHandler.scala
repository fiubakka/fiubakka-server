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

  def apply(implicit system: ActorSystem[GameServer.Command], connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val player = ctx.spawn(Player(Player.Position(1, 2)), "mainPlayer")

      val echo = Flow[ByteString]
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
        .map(_.utf8String)
        .map(_ + "!!!\n")
        .map(ByteString(_))

      //TODO: Parse received input and send corresponding message to Player actor

      connection.handleWith(echo)
      Behaviors.empty
    })
  }
}
