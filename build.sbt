import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.MarcosRolando"
ThisBuild / organizationName := "MarcosRolando"
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
    assembly / mainClass := Some("Main"),
    // See https://stackoverflow.com/questions/25144484/sbt-assembly-deduplication-found-error
    // Basically we are telling sbt-assembly to ignore the META-INF folder for conflicting files
    //
    // See https://stackoverflow.com/questions/31011243/no-configuration-setting-found-for-key-akka-version
    // We need to concat both reference.conf and version.conf files for Akka config to work 
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*)   => MergeStrategy.discard
      case "reference.conf"                => MergeStrategy.concat
      case "version.conf"                  => MergeStrategy.concat
      case _                               => MergeStrategy.first
    },
    name := "akka-backend-tp",
    libraryDependencies ++= Seq(
      akkaTyped,
      akkaStream,
      akkaCluster,
      akkaClusterSharding,
      akkaManagementHttp,
      akkaManagementBootstrap,
      akkaKubernetesDiscovery,
      akkaKubernetesRollingUpdates,
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
