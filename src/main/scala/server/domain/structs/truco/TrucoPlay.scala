package server.domain.structs.truco

enum TrucoShoutEnum {
  case Envido
  case RealEnvido
  case FaltaEnvido
  case Truco
  case Retruco
  case ValeCuatro
  case Quiero
  case NoQuiero
}

final case class TrucoCardPlay(
    card: Int
)

final case class TrucoShoutPlay(
    shout: TrucoShoutEnum
)

type TrucoPlay = TrucoCardPlay | TrucoShoutPlay
