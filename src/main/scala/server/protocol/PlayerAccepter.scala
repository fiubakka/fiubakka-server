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
import server.GameServer
import scala.util.Success
import scala.util.Failure
import akka.util.Timeout
import java.time.Duration

object PlayerAccepter {
  sealed trait Command
  final case class Accept(connection: Tcp.IncomingConnection) extends Command
  final case class HandlerResponse(ip: String, port: Int) extends Command

  def apply(implicit system: ActorSystem[GameServer.Command]): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val timeout = Timeout.create(Duration.ofSeconds(3))

      val connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp(system).bind("localhost", 8080)
      connections.runForeach { connection =>
        ctx.self ! Accept(connection)
      }

      Behaviors.receiveMessage {
        case Accept(connection: Tcp.IncomingConnection) => {
          ctx.log.info(s"Accepting new conenction from ${connection.remoteAddress}")
          val playerHandler = ctx.spawn(PlayerHandler(), s"playerHandler${connection.remoteAddress.getPort()}")
          // Ask the playerHandler to handle the connection, using ask function
          ctx.ask(playerHandler, PlayerHandler.GetIPInfo.apply) {
            case Success(PlayerHandler.Response(ip, port)) => HandlerResponse(ip, port)
            case Failure(_)                                => HandlerResponse("error", 0)
          }
          Behaviors.same
        }

        case HandlerResponse(ip, port) => {
          ctx.log.info(s"Player handler response: $ip, $port")
          Behaviors.same
        }
      }
    })
  }
}
