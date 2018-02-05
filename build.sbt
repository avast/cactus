import sbt.Keys._

crossScalaVersions := Seq("2.11.11", "2.12.4")

lazy val Versions = new {
  val gpb3Version = "3.3.0"
  val grpcVersion = "1.9.0"
}

lazy val commonSettings = Seq(
  scalaVersion := "2.11.11",
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",
  resolvers += Resolver.jcenterRepo,

  organization := "com.avast.cactus",
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1-SNAPSHOT"),
  description := "Library for conversion between GPB and Scala case classes",

  licenses ++= Seq("Apache-2.0" -> url(s"https://github.com/avast/${name.value}/blob/${version.value}/LICENSE")),
  publishArtifact in Test := false,
  bintrayOrganization := Some("avast"),
  pomExtra := (
    <scm>
      <url>git@github.com:avast/
        {name.value}
        .git</url>
      <connection>scm:git:git@github.com:avast/
        {name.value}
        .git</connection>
    </scm>
      <developers>
        <developer>
          <id>avast</id>
          <name>Jan Kolena, Avast Software s.r.o.</name>
          <url>https://www.avast.com</url>
        </developer>
      </developers>
    ),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-library" % scalaVersion.value,
    "org.scalactic" %% "scalactic" % "3.0.4",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    "org.mockito" % "mockito-core" % "2.13.0" % "test"
  )
)

lazy val macroSettings = Seq(
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary)
)

lazy val root = Project(id = "rootProject",
  base = file(".")) settings (publish := {}) aggregate(commonModule, v2Module, v3Module, bytesModule, bytesV3Module, grpcCommonModule, grpcClientModule, grpcServerModule)

lazy val commonModule = Project(
  id = "common",
  base = file("./common"),
  settings = commonSettings ++ macroSettings ++ Seq(
    name := "cactus-common",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % Versions.gpb3Version % "optional",
      "com.google.protobuf" % "protobuf-java-util" % Versions.gpb3Version % "optional",

      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    )
  )
)

lazy val v2Module = Project(
  id = "gpbv2",
  base = file("./gpbv2"),
  settings = commonSettings ++ Seq(
    name := "cactus-gpbv2",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "2.6.1" % "optional"
    )
  )
).dependsOn(commonModule, bytesModule % "test")

lazy val v3Module = Project(
  id = "gpbv3",
  base = file("./gpbv3"),
  settings = commonSettings ++ Seq(
    name := "cactus-gpbv3",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % Versions.gpb3Version,
      "com.google.protobuf" % "protobuf-java-util" % Versions.gpb3Version
    )
  )
).dependsOn(commonModule)

lazy val bytesModule = Project(
  id = "bytes",
  base = file("./bytes"),
  settings = commonSettings ++ Seq(
    name := "cactus-bytes",
    libraryDependencies ++= Seq(
      "com.avast.bytes" % "bytes-gpb" % "2.0.3"
    )
  )
).dependsOn(commonModule)

lazy val bytesV3Module = Project(
  id = "bytes-gpbv3",
  base = file("./bytes-gpbv3"),
  settings = commonSettings ++ Seq(
    name := "cactus-bytes-gpbv3"
  )
).dependsOn(v3Module, bytesModule)

lazy val grpcCommonModule = Project(
  id = "grpc-common",
  base = file("./grpc-common"),
  settings = commonSettings ++ macroSettings ++ Seq(
    name := "cactus-grpc-common",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.0.1",
      "io.grpc" % "grpc-netty-shaded" % Versions.grpcVersion,
      "io.grpc" % "grpc-protobuf" % Versions.grpcVersion,
      "io.grpc" % "grpc-stub" % Versions.grpcVersion % "optional",
      "io.grpc" % "grpc-services" % Versions.grpcVersion % "optional"
    )
  )
).dependsOn(v3Module)

lazy val grpcClientModule = Project(
  id = "grpc-client",
  base = file("./grpc-client"),
  settings = commonSettings ++ macroSettings ++ Seq(
    name := "cactus-grpc-client",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-stub" % Versions.grpcVersion,
      "io.grpc" % "grpc-services" % Versions.grpcVersion % "test"
    )
  )
).dependsOn(grpcCommonModule)

lazy val grpcServerModule = Project(
  id = "grpc-server",
  base = file("./grpc-server"),
  settings = commonSettings ++ macroSettings ++ Seq(
    name := "cactus-grpc-server",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-services" % Versions.grpcVersion,
      "io.grpc" % "grpc-stub" % Versions.grpcVersion % "test"
    )
  )
).dependsOn(grpcCommonModule)
