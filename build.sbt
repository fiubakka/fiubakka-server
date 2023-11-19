import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"
ThisBuild / scalacOptions    += "-Ywarn-unused"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

inThisBuild(
  List(
    scalaVersion := "2.13.12",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
    )
  )


lazy val root = (project in file("."))
  .settings(
    name := "akka-backend-tp",
    libraryDependencies ++= Seq(
      akkaTyped,
      akkaStream,
      akkaCluster,
      akkaSerialization,
      akkaPersistence,
      akkaPersistenceJdbc,
      akkaPersistenceQuery,
      postgresJdbc,
      slick,
      slickHikaricp,
      log4jApi,
      logback,
      munit % Test,
      akkaStreamTestKit % Test,
      akkaPersistenceTestKit % Test
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
