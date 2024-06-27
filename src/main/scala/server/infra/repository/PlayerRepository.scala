package server.infra.repository

import org.mindrot.jbcrypt.BCrypt
import server.infra.DB
import server.infra.model.Player
import server.infra.model.Players
import slick.jdbc.PostgresProfile.api.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PlayerRepository {
  final case class UserAlreadyExistsException(username: String)
      extends Exception(s"Player $username already exists")

  val db = DB()
  def players = TableQuery[Players]

  def create(username: String, password: String): Future[Int] = {
    findByPlayerName(username).flatMap {
      case Some(_) =>
        Future.failed(new UserAlreadyExistsException(username))
      case None =>
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        db.run(players += Player(None, username, hashedPassword))
    }
  }

  def validate(username: String, password: String): Future[Boolean] = {
    findByPlayerName(username).map {
      case Some(player) => BCrypt.checkpw(password, player.password)
      case None         => false
    }
  }

  def findByPlayerName(playerName: String): Future[Option[Player]] = {
    db.run(players.filter(_.username === playerName).result.headOption)
  }
}
