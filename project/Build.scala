import sbt.Keys._
import sbt._


object Build extends Build {


  val commonSettings = Defaults.coreDefaultSettings ++ Seq(
    scalaVersion := "2.11.7"
    , version := "0.1"
    , scalacOptions += "-deprecation"
    , scalacOptions += "-unchecked"
    , scalacOptions += "-feature"
    , scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")
    , resolvers += Resolver.sonatypeRepo("releases")
  )

  val macroSettings = Seq(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % "2.11.7",
      "com.google.protobuf" % "protobuf-java" % "2.6.1",
      "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test"
    )
  )

  lazy val macros = Project(
    id = "macros"
    , base = file("./macros")
    , settings = commonSettings ++ macroSettings ++ Seq(
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    )
  )
}
