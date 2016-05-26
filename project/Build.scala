import sbt.Keys._
import sbt._


object Build extends Build  {


  val commonSettings = Defaults.coreDefaultSettings ++ Seq(
    scalaVersion := "2.11.7"
    , version :=  "0.1"
    , scalacOptions += "-deprecation"
    , scalacOptions += "-unchecked"
    , scalacOptions += "-feature"
    , scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")
    , resolvers += Resolver.sonatypeRepo("releases")
  )

  val macroSettings = Seq(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % "2.11.7"
    )
  )

  lazy val macros = Project(
    id = "macros"
    , base = file("./macros")
    , settings = commonSettings ++ macroSettings ++ Seq(
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    )
  )

  lazy val demo = Project(
    id = "example"
    , base = file("./example")
    , settings = commonSettings ++ Seq(
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    )
  ).dependsOn(macros)

}
