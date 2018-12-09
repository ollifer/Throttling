name := "Throttling"

version := "0.1"

scalaVersion := "2.12.8"

val akkaHttpVersion = "10.1.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % "2.5.12",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)