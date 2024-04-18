package server.domain.entities.player.utils

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import server.domain.entities.player.Player._
import server.protocol.event.GameEventConsumer
import server.protocol.event.GameEventProducer

import scala.util.Random

object PlayerUtils {
  def getEventHandlers(ctx: ActorContext[Command], mapId: Int): (
      ActorRef[GameEventConsumer.Command],
      ActorRef[GameEventProducer.Command]
  ) = {
    val eventHandlersSuffix = Random.alphanumeric.take(8).mkString
    val eventConsumer = ctx.spawn(
      GameEventConsumer(
        ctx.self,
        mapId
      ),
      s"GameEventConsumer-$mapId-$eventHandlersSuffix"
    )
    val eventProducer = ctx.spawn(
      GameEventProducer(
        ctx.self.path.name,
        mapId
      ),
      s"GameEventProducer-$mapId-$eventHandlersSuffix"
    )
    (eventConsumer, eventProducer)
  }

}
