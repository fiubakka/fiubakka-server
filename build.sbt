import Dependencies._

ThisBuild / scalaVersion     := "3.4.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.MarcosRolando"
ThisBuild / organizationName := "MarcosRolando"
// Currently ScalaPB generates deprecation warnings for Scala 3.4.1
// -rewrite and -source:3.4-migration fixes them but they are not silenced.
// These warnings cannot be silenced easily until https://github.com/scala/scala3/pull/18783 is merged.
// For now as a workaround we silence them via regex (to avoid silencing other deprecation warnings).
ThisBuild / scalacOptions    ++= Seq(
  "-rewrite",
  "-source:3.4-migration", 
  "-new-syntax",
  "-Wunused:all",
  "-deprecation",
  "-feature",
  "-Wconf:help",
  "-Wconf:msg=`_` is deprecated for wildcard arguments of types:silent", // From ScalaPB, fixed by -rewrite -source:3.4-migration
  "-Wconf:msg=this-qualifier:silent", // From ScalaPB, fixed by -rewrite -source:3.4-migration
  "-Werror",
)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

inThisBuild(
  List(
    scalaVersion := "3.4.1",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
    )
  )

// See https://developer.lightbend.com/docs/telemetry/current//setup/cinnamon-agent-sbt.html
// for reference
// Lightbend Telemetry config
cinnamonSuppressRepoWarnings := true
run / cinnamon := false // Set to True to enable Cinnamon agent used for Telemetry
cinnamonLogLevel := "INFO"

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
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    name := "fiubakka-server",
    libraryDependencies ++= Seq(
      aeronDriver,
      aeronClient,
      akkaTyped,
      akkaStream,
      akkaStreamAlpakkaKafka,
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
      Cinnamon.library.cinnamonAkka,
      Cinnamon.library.cinnamonAkkaTyped,
      Cinnamon.library.cinnamonAkkaPersistence,
      Cinnamon.library.cinnamonAkkaStream,
      Cinnamon.library.cinnamonPrometheus,
      Cinnamon.library.cinnamonAkkaCluster,
      Cinnamon.library.cinnamonPrometheusHttpServer,
      postgresJdbc,
      slick,
      slickHikaricp,
      bcrypt,
      log4jApi,
      logback,
      janino,
    )
  )
  .enablePlugins(
    Cinnamon
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
