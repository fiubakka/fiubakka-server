package server.protocol.client
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp

import scala.concurrent.Future

object PlayerAcceptor {
  sealed trait Command
  final case class Accept(connection: Tcp.IncomingConnection) extends Command

  def apply(): Behavior[Command] = {
    Behaviors.setup(ctx => {
      implicit val mat = Materializer(ctx)

      val connections
          : Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] =
        Tcp(ctx.system).bind(
          "0.0.0.0",
          ctx.system.settings.config.getInt("game.player-acceptor.port")
        )
      connections.runForeach { connection =>
        ctx.self ! Accept(connection)
      }

      Behaviors.receiveMessage {
        case Accept(connection: Tcp.IncomingConnection) => {
          ctx.spawn(
            PlayerHandler(connection),
            s"PlayerHandler-${connection.remoteAddress
                .getAddress()
                .getHostAddress()}-${connection.remoteAddress.getPort()}"
          )
          Behaviors.same
        }
      }
    })
  }
}
