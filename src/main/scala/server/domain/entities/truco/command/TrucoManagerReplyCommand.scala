package server.domain.entities.truco.command

import akka.serialization.jackson.CborSerializable
import server.protocol.truco.TrucoPlayState

object TrucoManagerReplyCommand {
  sealed trait ReplyCommand extends CborSerializable

  final case class TrucoPlayStateInfo(playState: TrucoPlayState)
      extends ReplyCommand
  final case class TrucoAllowPlay(playId: Int) extends ReplyCommand
  final case class TrucoPlayerDisconnected(opponentUsername: String)
      extends ReplyCommand
}
