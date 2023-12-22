package server.misc

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.Sharding
import server.domain.entities.Player
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity

import scala.concurrent.duration._
import scala.util.Random

object Bot {
  sealed trait Command

  final case class RandomMove() extends Command
  final case class PlayerReplyCommand(cmd: Player.ReplyCommand) extends Command

  final case class State(
      playerBot: EntityRef[Player.Command],
      position: Position
  )

  def apply(): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        val playerBot = Sharding().entityRefFor(
          Player.TypeKey,
          Random.alphanumeric.take(20).mkString
        )

        val playerResponseMapper: ActorRef[Player.ReplyCommand] =
          ctx.messageAdapter(rsp => PlayerReplyCommand(rsp))

        playerBot ! Player.Start(playerResponseMapper)
        timers.startTimerWithFixedDelay(RandomMove(), 16.millis)

        behaviour(
          State(
            playerBot,
            Position(20, Random.nextInt(100).toFloat)
          )
        )

      }
    }
  }

  def behaviour(state: State): Behavior[Command] = {
    Behaviors.receiveMessage {
      case RandomMove() =>
        // Generate random velocity with magnitude 1
        val randVelocity = Velocity(2, 0)
        val newPosition = Position(
          state.position.x + randVelocity.x,
          state.position.y + randVelocity.y
        )
        state.playerBot ! Player.Move(
          velocity = randVelocity,
          position = newPosition
        )
        behaviour(state.copy(position = newPosition))

      case _ => Behaviors.same
    }
  }
}
