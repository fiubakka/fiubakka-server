package server.infra

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object DB {
  private var dc: Option[DatabaseConfig[JdbcProfile]] = None

  def apply() = {
    dc.getOrElse(
      throw new IllegalStateException(
        "Database not initialized. Call configure method first."
      )
    ).db
  }

  def configure() = {
    // See https://scala-slick.org/doc/stable/database.html#databaseconfig for reference
    dc = Some(DatabaseConfig.forConfig[JdbcProfile]("slick"))
  }
}
