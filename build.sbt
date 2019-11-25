name := "streamingtwitter"

version := "0.1"

scalaVersion := "2.12.8"

val http4sVersion = "0.20.13"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-generic" % "0.11.1",
  "io.circe" %% "circe-config" % "0.7.0",

  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

scalacOptions += "-Ypartial-unification"
