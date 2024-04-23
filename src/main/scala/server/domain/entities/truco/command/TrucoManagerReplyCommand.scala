package server.domain.entities.truco.command

import akka.serialization.jackson.CborSerializable
import server.protocol.truco.TrucoPlayState

object TrucoManagerReplyCommand {
  sealed trait ReplyCommand extends CborSerializable

  final case class PlayStateInfo(playState: TrucoPlayState) extends ReplyCommand
}
