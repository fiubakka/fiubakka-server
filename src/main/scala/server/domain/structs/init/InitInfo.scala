package server.domain.structs.init

import server.domain.structs.inventory.Equipment

sealed trait InitInfo {
  val playerName: String
  def getInitialEquipment(): Option[Equipment]
}

final case class RegisterInfo(
    playerName: String,
    password: String,
    equipment: Equipment
) extends InitInfo {
  override def getInitialEquipment(): Option[Equipment] = Some(equipment)
}

final case class LoginInfo(playerName: String, password: String)
    extends InitInfo {
  override def getInitialEquipment(): Option[Equipment] = None
}

object InitInfo {
  def fromRegisterInfo(
      playerName: String,
      password: String,
      equipment: Equipment
  ): InitInfo =
    RegisterInfo(playerName, password, equipment)

  def fromLoginInfo(playerName: String, password: String): InitInfo =
    LoginInfo(playerName, password)
}
