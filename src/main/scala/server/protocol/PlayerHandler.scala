package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import server.Sharding
import server.domain.entities.Player

import scala.concurrent.ExecutionContext.Implicits.global

object PlayerHandler {
  sealed trait Command
  final case class ConnectionClosed() extends Command

  def apply(connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      implicit val system = ctx.system

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

      val player = Sharding().entityRefFor(
        Player.TypeKey,
        "player"
      ) // TODO use random entityId

      Behaviors.receiveMessage {
        case ConnectionClosed() => {
          ctx.log.info("Closing connection!")
          player ! Player.PrintPosition()
          player ! Player.Move(Player.Position(1, 1))
          player ! Player.PrintPosition()
          Behaviors.same
        }
      }
    }
  }
}
