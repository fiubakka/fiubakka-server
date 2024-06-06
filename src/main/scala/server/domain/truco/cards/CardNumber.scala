package server.domain.truco.cards

enum CardNumber:
  case Ace, Two, Three, Four, Five, Six, Seven, Ten, Eleven, Twelve

  def envidoValue: Int = this match
    case Ace    => 1
    case Two    => 2
    case Three  => 3
    case Four   => 4
    case Five   => 5
    case Six    => 6
    case Seven  => 7
    case Ten    => 0
    case Eleven => 0
    case Twelve => 0
