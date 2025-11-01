ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .enablePlugins()
  .settings(
    name := "FunctionalImageTransformer",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-core" % "0.23.32",
      "org.http4s" %% "http4s-ember-server" % "0.23.32",
      "org.http4s" %% "http4s-dsl" % "0.23.32",
      "org.typelevel" %% "cats-effect" % "3.6.3",
      "org.http4s" %% "http4s-blaze-server" % "0.23.17",
      "org.http4s" %% "http4s-server" % "0.23.32"
    )
  )
