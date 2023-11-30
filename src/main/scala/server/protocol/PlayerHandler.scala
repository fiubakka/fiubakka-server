package server.protocol

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.serialization.jackson.CborSerializable
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import server.Sharding
import server.domain.entities.Player

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PlayerHandler {
  sealed trait Command extends CborSerializable
  final case class ConnectionClosed() extends Command

  final case class Init(x: Int, y: Int) extends Command
  final case class StartMoving(x: Int, y: Int) extends Command
  final case class StopMoving() extends Command
  final case class Move(x: Int, y: Int) extends Command

  final case class MoveReply(x: Int, y: Int) extends Command

  def apply(connection: Tcp.IncomingConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val system = ctx.system

        val (conQueue, conSource) = Source
          .queue[ByteString](256, OverflowStrategy.backpressure)
          .preMaterialize()

        val clientResponse = Flow[ByteString]
          .via(
            Framing.delimiter(ByteString("\n"), 256, allowTruncation = true)
          )
          .map(_.utf8String)
          .map { str =>
            val parts = str.split("\\s+")

            parts.headOption match {
              case Some("INIT") if parts.length == 5 =>
                Some(Init(parts(1).toInt, parts(2).toInt))

              case Some("VEL")
                  if parts.length == 3 && parts(1).toInt == 0 && parts(
                    2
                  ).toInt == 0 =>
                Some(StopMoving())

              case Some("VEL") if parts.length == 3 =>
                Some(StartMoving(parts(1).toInt, parts(2).toInt))

              case _ =>
                None // TODO handle bad input
            }
          }
          .collect { case Some(cmd) => cmd }
          .map { cmd =>
            ctx.self ! cmd
            ()
          }
          .filter(_ => false) // TODO define protocol
          .map(_ + "!!!\n")
          .map(ByteString(_))
          .merge(conSource)
          .watchTermination() { (_, done) =>
            done.onComplete(_ => ctx.self ! ConnectionClosed())
          }

        connection.handleWith(clientResponse)

        val player = Sharding().entityRefFor(
          Player.TypeKey,
          "player2"
        ) // TODO use random entityId

        val eventConsumer = Sharding().entityRefFor(
          GameEventConsumer.TypeKey,
          "player2"
        ) // TODO capaz lo podemos volar si es que el init no es lazy

        val eventProducer = Sharding().entityRefFor(
          GameEventProducer.TypeKey,
          "player2"
        )

        eventConsumer ! GameEventConsumer.EventReceived("CREATED")
        eventProducer ! GameEventProducer.ProduceEvent("INIT")

        Behaviors.receiveMessage {
          case ConnectionClosed() => {
            ctx.log.info("Closing connection!")
            Behaviors.stopped
          }

          case Init(x, y) => {
            ctx.log.info(s"Init message received $x, $y!")
            Behaviors.same
          }

          case StartMoving(x, y) => {
            ctx.log.info(s"StartMoving message received $x, $y!")
            timers.startTimerAtFixedRate("move", Move(x, y), 16666.micro)
            Behaviors.same
          }

          case StopMoving() => {
            ctx.log.info("StopMoving message received!")
            timers.cancel("move")
            Behaviors.same
          }

          case Move(x, y) => {
            player ! Player.Move(x, y, ctx.self)
            Behaviors.same
          }

          case MoveReply(x, y) => {
            conQueue.offer(ByteString(s"POS $x $y\n"))
            Behaviors.same
          }
        }
      }
    }
  }
}
