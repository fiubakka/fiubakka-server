import sbt._

object Dependencies {
  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"
  lazy val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % "2.9.0"
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.9.0"
  lazy val akkaCluster = "com.typesafe.akka" %% "akka-cluster-typed" % "2.9.0"
  lazy val log4jApi = "org.apache.logging.log4j" % "log4j-api" % "2.21.1"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % "2.9.0"
}
