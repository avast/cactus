import sbt.Keys._

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",
  scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off"),
  resolvers += Resolver.sonatypeRepo("releases"),

  crossScalaVersions := Seq("2.11.8", "2.12.0"),

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
    "org.scala-lang" % "scala-reflect" % "2.11.8",
    "com.google.protobuf" % "protobuf-java" % "2.6.1",
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
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
)