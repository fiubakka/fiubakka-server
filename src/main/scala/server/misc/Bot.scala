package server.misc

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import server.domain.entities.InitData
import server.domain.entities.Player
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity
import server.sharding.Sharding

import scala.concurrent.duration._
import scala.util.Random

object Bot {
  sealed trait Command
  private type CommandOrPlayerReply = Command | Player.ReplyCommand

  final case class RandomMove() extends Command
  final case class Heartbeat() extends Command
  final case class PlayerReplyCommand(cmd: Player.ReplyCommand) extends Command

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

        playerBot ! Player.Init(
          InitData(
            ctx.self,
            None
          )
        )
        timers.startTimerWithFixedDelay(Heartbeat(), 2.seconds)
        timers.startTimerWithFixedDelay(RandomMove(), 16.millis)

        behaviour(
          State(
            playerBot,
            Position(20, 20)
          )
        )

      }
    }
  }

  def behaviour(
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
          state.playerBot ! Player.Move(
            velocity = randVelocity,
            position = newPosition
          )
          behaviour(state.copy(position = newPosition))

        case Heartbeat() => {
          state.playerBot ! Player.Heartbeat(ctx.self)
          Behaviors.same
        }

        case _ => Behaviors.same
      }
    }
  }
}
