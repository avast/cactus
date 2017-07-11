import sbt.Keys._

crossScalaVersions := Seq("2.11.11", "2.12.2")

lazy val commonSettings = Seq(
  scalaVersion := "2.11.11",
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",

  organization := "com.avast",
  name := "cactus",
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1-SNAPSHOT"),
  description := "Library for conversion between GPB and Scala case classes",

  licenses ++= Seq("Apache-2.0" -> url(s"https://github.com/avast/${name.value}/blob/${version.value}/LICENSE")),
  publishArtifact in Test := false,
  bintrayOrganization := Some("avast"),
  pomExtra := (
    <scm>
      <url>git@github.com:avast/{name.value}.git</url>
      <connection>scm:git:git@github.com:avast/{name.value}.git</connection>
    </scm>
      <developers>
        <developer>
          <id>avast</id>
          <name>Jan Kolena, Avast Software s.r.o.</name>
          <url>https://www.avast.com</url>
        </developer>
      </developers>
    )
)

lazy val macroSettings = Seq(
  libraryDependencies ++= Seq(
    "com.google.protobuf" % "protobuf-java" % "3.3.0" % "optional",
    "com.google.protobuf" % "protobuf-java-util" % "3.3.0" % "optional",

    "com.avast.bytes" % "bytes-gpb" % "2.0.3" % "optional",

    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    "org.scalactic" %% "scalactic" % "3.0.0",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )
)

lazy val root = Project(id = "rootProject",
  base = file(".")) settings (publish := { }) aggregate macros

lazy val macros = Project(
  id = "macros",
  base = file("./macros"),
  settings = commonSettings ++ macroSettings ++ Seq(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )
)