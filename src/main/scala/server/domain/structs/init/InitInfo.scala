package server.domain.structs.init

import server.domain.structs.inventory.Equipment

sealed trait InitInfo {
  val playerName: String
  def getInitialEquipment(): Option[Equipment]
}

final case class RegisterInfo(playerName: String, equipment: Equipment)
    extends InitInfo {
  override def getInitialEquipment(): Option[Equipment] = Some(equipment)
}

final case class LoginInfo(playerName: String) extends InitInfo {
  override def getInitialEquipment(): Option[Equipment] = None
}

object InitInfo {
  def fromRegisterInfo(playerName: String, equipment: Equipment): InitInfo =
    RegisterInfo(playerName, equipment)

  def fromLoginInfo(playerName: String): InitInfo = LoginInfo(playerName)
}
