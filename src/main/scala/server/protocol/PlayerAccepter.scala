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
  final case class Accept(connection: Tcp.IncomingConnection) extends Command

  def apply(implicit system: ActorSystem[GameServer.Command]): Behavior[Command] = {
    Behaviors.setup(ctx => {
      val connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp(system).bind("localhost", 8080)
      connections.runForeach { connection =>
        ctx.self ! Accept(connection)
      }

      //Should this be inside Behaviors.setup or outside?
      Behaviors.receiveMessage {
        case Accept(connection: Tcp.IncomingConnection) => {
          println(s"New connection from: ${connection.remoteAddress}")
          ctx.spawn(PlayerHandler(system, connection), s"handler${connection.remoteAddress.getPort()}")
          Behaviors.same
        }
        case Run() => {//This is to avoid the warning that the case is not handling every command. Is there a way to avoid explicitly having this?
          Behaviors.empty
        }
      }
    })
  }
}
