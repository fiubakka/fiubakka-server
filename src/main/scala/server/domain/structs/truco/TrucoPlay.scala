package server.domain.structs.truco

import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.Mazo as MazoEnum
import server.domain.truco.shouts.TrucoEnum

enum TrucoShoutEnum {
  case Mazo
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
  val toShoutPlayEnum
      : PartialFunction[TrucoShoutEnum, TrucoEnum | EnvidoEnum] = {
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

  def fromShoutPlayEnum
      : (shout: TrucoEnum | EnvidoEnum | MazoEnum) => TrucoShoutEnum = {
    case MazoEnum()             => Mazo
    case EnvidoEnum.Envido      => Envido
    case EnvidoEnum.RealEnvido  => RealEnvido
    case EnvidoEnum.FaltaEnvido => FaltaEnvido
    case EnvidoEnum.Quiero      => EnvidoQuiero
    case EnvidoEnum.NoQuiero    => EnvidoNoQuiero
    case TrucoEnum.Truco        => Truco
    case TrucoEnum.Retruco      => Retruco
    case TrucoEnum.Valecuatro   => Valecuatro
    case TrucoEnum.Quiero       => TrucoQuiero
    case TrucoEnum.NoQuiero     => TrucoNoQuiero
  }
}

final case class TrucoCardPlay(
    card: Int
)

final case class TrucoShoutPlay(
    shout: TrucoShoutEnum
)

// We don't use a Union here because Jackson Databind serializes them in a weird way
// that breaks the pattern matching. Unions from Scala 3 are not yet supported by Jackson.
type TrucoPlay = Either[TrucoCardPlay, TrucoShoutPlay]
