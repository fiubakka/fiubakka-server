package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Tcp
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import server.GameServer

object PlayerAccepter {
  sealed trait Command
  final case class Run() extends Command

  def apply(implicit system: ActorSystem[GameServer.Command]): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp(system).bind("localhost", 8080)
      connections.runForeach { connection =>
        println(s"New connection from: ${connection.remoteAddress}")

        val echo = Flow[ByteString]
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .map(_.utf8String)
          .map(_ + "!!!\n")
          .map(ByteString(_))

        connection.handleWith(echo)
      }

      Behaviors.empty
    })
  }
}
