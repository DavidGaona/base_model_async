ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

lazy val root = (project in file("."))
  .settings(
    name := "base_model_async"
  )

resolvers += "Akka library repository".at("https://repo.akka.io/maven")
val AkkaVersion = "2.8.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-remote" % AkkaVersion
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.6"
libraryDependencies += "org.postgresql" % "postgresql" % "42.5.4"
