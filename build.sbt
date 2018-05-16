lazy val root = project.in(file("."))
name := "stomp-for-akka-streams"

organization := "com.nachinius"
description := "Provide akka streams source, sink, and flow for connecting to STOMP PROTOCOL 1.2 server and clients"
val repo = "StompConnectorForAkkaStreams"
val username = "nachinius"

scalaVersion := "2.12.4"
val akkaVersion = "2.5.9"

libraryDependencies ++= Seq(
  // https://mvnrepository.com/artifact/io.vertx/vertx-stomp
  "io.vertx" % "vertx-stomp" % "3.5.1" // ApacheV2/EPL1
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)

val scalaTestVersion = "3.0.4"
//libraryDependencies += "org.scalactic" %% "scalactic" % scalaTestVersion
libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion % "test"

//val scalaCheckVersion = "1.13.4"
//libraryDependencies += "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test"
//libraryDependencies += "org.scalacheck" %% "scalacheck" % scalaCheckVersion

libraryDependencies += "org.parboiled" %% "parboiled" % "2.1.4"

homepage := Some(url(s"https://github.com/$username/$repo"))
licenses += "Apache License 2.0" -> url(s"https://github.com/$username/$repo/blob/master/LICENSE")
scmInfo := Some(ScmInfo(url(s"https://github.com/$username/$repo"), s"git@github.com:$username/$repo.git"))
apiURL := Some(url(s"https://$username.github.io/$repo/latest/api/"))
releaseCrossBuild := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
developers := List(
  Developer(id = username, name = "Ignacio `nachinius` Peixoto", email = "ignacio.peixoto@gmail.com", url = new URL(s"http://github.com/${username}"))
)

publishMavenStyle := true
publishArtifact in Test := true

publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)


