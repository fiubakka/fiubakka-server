package server.domain.structs.truco

import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum

enum TrucoShoutEnum {
  case Envido
  case RealEnvido
  case FaltaEnvido
  case EnvidoQuiero
  case EnvidoNoQuiero
  case Truco
  case Retruco
  case Valecuatro
  case TrucoQuiero
  case TrucoNoQuiero
}

object TrucoShoutEnum {
  def toShoutPlayEnum(shout: TrucoShoutEnum) = {
    shout match {
      case Envido         => EnvidoEnum.Envido
      case RealEnvido     => EnvidoEnum.RealEnvido
      case FaltaEnvido    => EnvidoEnum.FaltaEnvido
      case EnvidoQuiero   => EnvidoEnum.Quiero
      case EnvidoNoQuiero => EnvidoEnum.NoQuiero
      case Truco          => TrucoEnum.Truco
      case Retruco        => TrucoEnum.Retruco
      case Valecuatro     => TrucoEnum.Valecuatro
      case TrucoQuiero    => TrucoEnum.Quiero
      case TrucoNoQuiero  => TrucoEnum.NoQuiero
    }
  }
}

final case class TrucoCardPlay(
    card: Int
)

final case class TrucoShoutPlay(
    shout: TrucoShoutEnum
)

type TrucoPlay = TrucoCardPlay | TrucoShoutPlay
