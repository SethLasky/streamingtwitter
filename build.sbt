name := "streamingtwitter"

version := "0.1"

scalaVersion := "2.12.8"

val http4sVersion = "0.20.13"
val circeVersion = "0.11.1"

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "18.0",
  "io.circe" %% "circe-config" % "0.6.0",
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

scalacOptions += "-Ypartial-unification"
unmanagedClasspath in Runtime += baseDirectory.value / "etc"
