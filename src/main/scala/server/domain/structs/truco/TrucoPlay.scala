package server.domain.structs.truco

import server.domain.truco.shouts.EnvidoEnum
import server.domain.truco.shouts.EnvidoEnum.EnvidoEnum
import server.domain.truco.shouts.TrucoEnum
import server.domain.truco.shouts.TrucoEnum.TrucoEnum

object TrucoShoutEnum extends Enumeration {
  type TrucoShoutEnum = Value
  val Mazo, Envido, RealEnvido, FaltaEnvido, EnvidoQuiero, EnvidoNoQuiero,
      Truco, Retruco, Valecuatro, TrucoQuiero, TrucoNoQuiero = Value

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

  def fromShoutPlayEnum: (shout: TrucoEnum | EnvidoEnum) => TrucoShoutEnum = {
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
    case _ =>
      throw new IllegalArgumentException(
        "Invalid shout enum"
      ) // Should never happen, but Enumeration are ints so compiler complains otherwise
  }
}

final case class TrucoCardPlay(
    card: Int
)

final case class TrucoShoutPlay(
    shout: TrucoShoutEnum.TrucoShoutEnum
)

// We don't use a Union here because Jackson Databind serializes them in a weird way
// that breaks the pattern matching.
type TrucoPlay = Either[TrucoCardPlay, TrucoShoutPlay]
