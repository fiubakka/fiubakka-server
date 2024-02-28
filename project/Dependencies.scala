import sbt._

object Dependencies {
  val AkkaVersion = "2.9.0"
  val AeronVersion = "1.42.1"
  val AkkaManagementVersion = "1.5.0"
  val SlickVersion = "3.4.1"
  val AlpakkaVersion = "5.0.0"
  val JacksonVersion = "2.11.4"

  // Use AeronUDP instead of TCP for network transport of Akka messages
  lazy val aeronDriver = "io.aeron" % "aeron-driver" % AeronVersion
  lazy val aeronClient = "io.aeron" % "aeron-client" % AeronVersion

  lazy val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % AkkaVersion
  lazy val akkaStreamAlpakkaKafka = "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaVersion

  lazy val akkaCluster = "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion
  lazy val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion

  lazy val akkaManagementHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion
  lazy val akkaManagementBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion
  lazy val akkaKubernetesDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion
  // This is necessary to avoid killing the oldest node, which is bad in Akka
  lazy val akkaKubernetesRollingUpdates =  "com.lightbend.akka.management" %% "akka-rolling-update-kubernetes" % AkkaManagementVersion
  lazy val akkaSerialization = "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion

  lazy val akkaPersistence = "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion
  lazy val akkaPersistenceJdbc = "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.3.0"
  lazy val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion
  lazy val postgresJdbc = "org.postgresql" % "postgresql" % "42.6.0"

  // These Slick dependencies are needed both for akka-persistence-jdbc and to access the database directly
  lazy val slick = "com.typesafe.slick" %% "slick" % SlickVersion
  lazy val slickHikaricp = "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion

  lazy val bcrypt = "org.mindrot" % "jbcrypt" % "0.4"

  lazy val log4jApi = "org.apache.logging.log4j" % "log4j-api" % "2.21.1"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.12"
  // This is needed for conditional statements in logback configuration file
  lazy val janino = "org.codehaus.janino" % "janino" % "3.1.8"
}
