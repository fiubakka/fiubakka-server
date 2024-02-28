package server.infra

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

object DB {
  private var db: Option[Database] = None

  def apply(): Database = {
    db.getOrElse(
      throw new IllegalStateException(
        "Database not initialized. Call configure method first."
      )
    )
  }

  def configure() = {
    // See https://scala-slick.org/doc/stable/database.html#databaseconfig for reference
    val dc = DatabaseConfig.forConfig[JdbcProfile]("slick")
    db = Some(dc.db)
  }
}
