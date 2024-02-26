package server.domain.structs.init

import server.domain.structs.inventory.Equipment

sealed trait InitInfo {
  val playerName: String
}

final case class RegisterInfo(playerName: String, equipment: Equipment)
    extends InitInfo
final case class LoginInfo(playerName: String) extends InitInfo

object InitInfo {
  def fromRegisterInfo(playerName: String, equipment: Equipment): InitInfo =
    RegisterInfo(playerName, equipment)

  def fromLoginInfo(playerName: String): InitInfo = LoginInfo(playerName)
}
