name := "akka-typed-blockchain"

version := "0.1"

scalaVersion := "2.13.5"

val akkaVersion = "2.6.13"

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "ch.qos.logback"    % "logback-classic" % "1.2.3",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  "commons-codec" % "commons-codec" % "1.15"
)
