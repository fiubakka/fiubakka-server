import Dependencies._

inThisBuild(
  List(
    scalaVersion     := "3.4.2",
    version          := "0.1.0-SNAPSHOT",
    organization     := "com.github.MarcosRolando",
    organizationName := "MarcosRolando",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
    )
  )

// Currently ScalaPB generates deprecation warnings for Scala 3.4.2
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
  "-Wconf:msg=`_` is deprecated for wildcard arguments of types:silent", // From ScalaPB, fixed by -rewrite -source:3.4-migration
  "-Wconf:msg=this-qualifier:silent", // From ScalaPB, fixed by -rewrite -source:3.4-migration
  "-Werror",
)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

val isMetricsEnabled = sys.env.isDefinedAt("METRICS_ENABLED")

// See https://developer.lightbend.com/docs/telemetry/current//setup/cinnamon-agent-sbt.html
// for reference
// Lightbend Telemetry config
cinnamonSuppressRepoWarnings := true
run / cinnamon := isMetricsEnabled // Set to True to enable Cinnamon agent used for Telemetry
cinnamonLogLevel := "INFO"

lazy val root = (project in file("."))
  .settings(
    run / fork := true, // These are only used in development mode, since production uses a JAR and not sbt
    javaOptions ++= {
      val defaultAkkaPort = 25520
      val defaultPlayerAccepterPort = 2020
      val akkaPort = sys.env.getOrElse("AKKA_PORT", defaultAkkaPort)
      val playerAccepterPort = sys.env.getOrElse("PLAYER_ACCEPTER_PORT", defaultPlayerAccepterPort)

      Seq(
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", // Required for Aeron to work in Java 17+
        s"-Dakka.cluster.seed-nodes.0=akka://fiubakka-server@127.0.0.1:$defaultAkkaPort",
        s"-Dakka.remote.artery.canonical.port=$akkaPort",
        s"-Dakka.remote.artery.bind.port=$akkaPort",
        s"-Dgame.player-accepter.port=$playerAccepterPort"
      )
    },
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
    dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.36", // See https://doc.akka.io/docs/akka/current/typed/logging.html#slf4j-api-compatibility
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
      jacksonModuleScala,
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
      logback,
      janino,
    )
  )
  .enablePlugins(
    Cinnamon
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
