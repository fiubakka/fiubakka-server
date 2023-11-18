package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Tcp
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import server.GameServer
import scala.util.Success
import scala.util.Failure
import akka.util.Timeout
import java.time.Duration

object PlayerAccepter {
  sealed trait Command
  final case class Accept(connection: Tcp.IncomingConnection) extends Command
  final case class HandlerResponse(
      ip: String,
      port: Int,
      connection: Tcp.IncomingConnection
  ) extends Command

  def apply(implicit
      system: ActorSystem[GameServer.Command]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val timeout = Timeout.create(Duration.ofSeconds(3))

      val connections
          : Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] =
        Tcp(system).bind("localhost", 8080)
      connections.runForeach { connection =>
        ctx.self ! Accept(connection)
      }

      Behaviors.receiveMessage {
        case Accept(connection: Tcp.IncomingConnection) => {
          val playerHandler = ctx.spawn(
            PlayerHandler(),
            s"playerHandler${connection.remoteAddress.getPort()}"
          )
          ctx.ask(playerHandler, PlayerHandler.GetIPInfo.apply) {
            case Success(PlayerHandler.GetIPInfoResponse(ip, port)) =>
              HandlerResponse(ip, port, connection)
            case Failure(_) => HandlerResponse("error", 0, connection)
          }
          Behaviors.same
        }

        case HandlerResponse(ip, port, connection) => {
          connection.handleWith(
            Flow[ByteString]
              .merge(Source.single(ByteString(s"$ip:$port\n")))
              .take(1)
          )
          Behaviors.same
        }
      }
    })
  }
}
