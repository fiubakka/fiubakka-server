package server.misc

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.player.Player
import server.domain.entities.player.command.PlayerCommand
import server.domain.entities.player.command.PlayerReplyCommand
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.sharding.Sharding

import scala.concurrent.duration._
import scala.util.Random

object Bot {
  sealed trait Command
  private type CommandOrPlayerReply = Command | PlayerReplyCommand.Command

  final case class RandomMove() extends Command
  final case class Heartbeat() extends Command

  final case class State(
      playerBot: EntityRef[Player.Command],
      position: Position
  )

  def apply(): Behavior[CommandOrPlayerReply] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        val playerBot = Sharding().entityRefFor(
          Player.TypeKey,
          Random.alphanumeric.take(20).mkString
        )

        playerBot ! PlayerCommand.Init(
          Player.InitData(
            ctx.self,
            None
          )
        )
        timers.startTimerWithFixedDelay(Heartbeat(), 2.seconds)
        timers.startTimerWithFixedDelay(RandomMove(), 16.millis)

        runningBehavior(
          State(
            playerBot,
            Position(20, 20)
          )
        )

      }
    }
  }

  def runningBehavior(
      state: State
  ): Behavior[CommandOrPlayerReply] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case RandomMove() =>
          // Generate random velocity with magnitude 1
          val randVelocity = Velocity(2, 0)
          val newPosition = Position(
            state.position.x + randVelocity.x,
            state.position.y + randVelocity.y
          )
          state.playerBot ! PlayerCommand.Move(
            velocity = randVelocity,
            position = newPosition
          )
          runningBehavior(state.copy(position = newPosition))

        case Heartbeat() => {
          state.playerBot ! PlayerCommand.Heartbeat(ctx.self)
          Behaviors.same
        }

        case _ => Behaviors.same
      }
    }
  }
}
