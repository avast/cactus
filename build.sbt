import sbt.Keys._

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",
  scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off"),
  resolvers += Resolver.sonatypeRepo("releases"),

  organization := "com.avast",
  name := "cactus",
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1"),
  description := "Library for conversion between GPB and Scala case classes",

  licenses ++= Seq("Apache-2.0" -> url(s"https://github.com/avast/${name.value}/blob/${version.value}/LICENSE")),
  publishArtifact in Test := false,
  publishMavenStyle := true,
  publishTo in ThisBuild := Some(Resolver.file("file", new File("target/release"))),
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
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )
)

lazy val macros = Project(
  id = "macros",
  base = file("./macros"),
  settings = commonSettings ++ macroSettings ++ Seq(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
)