package server.domain.entities.truco.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import server.domain.entities.truco.command.TrucoManagerCommand._
import server.domain.structs.truco.TrucoManagerState

object TrucoManagerRunningBehavior {
  def apply(state: TrucoManagerState): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      Behaviors.same // TODO
    }
  }
}
