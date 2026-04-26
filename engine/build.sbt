ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val http4sVersion    = "0.23.30"
val catsEffectVersion = "3.5.7"
val circeVersion     = "0.14.10"
val munitVersion     = "1.0.3"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    Compile / mainClass := Some("chesslab.api.Main"),
    name := "chesslab-engine",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % http4sVersion,
      "org.http4s"    %% "http4s-circe"        % http4sVersion,
      "io.circe"      %% "circe-generic"       % circeVersion,
      "io.circe"      %% "circe-parser"        % circeVersion,
      "org.typelevel" %% "cats-effect"         % catsEffectVersion,
      // Testing
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
    Compile / run / fork := true
  )
