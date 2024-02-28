package server.infra.model

import slick.jdbc.PostgresProfile.api._

final case class Player(id: Option[Long], playerName: String, password: String)

class Players(tag: Tag) extends Table[Player](tag, "players") {
  def id = column[Option[Long]]("id", O.AutoInc, O.PrimaryKey)
  def username = column[String]("username", O.Unique)
  def password = column[String]("password")

  def * = (id, username, password).mapTo[Player]
}
