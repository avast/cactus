import sbt.Keys._

val logger: Logger = ConsoleLogger()

lazy val Scala213 = "2.13.1"
lazy val Scala212 = "2.12.10"
lazy val SupportedScalaVersions = List(Scala213, Scala212)

lazy val Versions = new {
  val grpcVersion = "1.26.0"
  val gpb3Version = "3.9.1"
  val gpb2Version = "2.6.1"

  val bytesVersion = "2.0.6"
  
  val GPBv2 = gpb2Version.replace(".", "")
  val GPBv3 = gpb3Version.replace(".", "")
}

lazy val commonSettings = Seq(
  scalaVersion := Scala213,
  crossScalaVersions := SupportedScalaVersions,
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",
  scalacOptions += "-target:jvm-1.8",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  resolvers += Resolver.jcenterRepo,

  organization := "com.avast.cactus",
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1-SNAPSHOT"),
  description := "Library for conversion between Java GPB classes and Scala case classes",

  licenses ++= Seq("Apache-2.0" -> url(s"https://github.com/avast/${name.value}/blob/${version.value}/LICENSE")),
  publishArtifact in Test := false,
  bintrayOrganization := Some("avast"),
  bintrayPackage := "cactus",
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
    "org.typelevel" %% "cats-core" % "2.0.0",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.3",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.mockito" % "mockito-core" % "2.18.3" % "test",
    "javax.annotation" % "javax.annotation-api" % "1.3.2" % "test" // for compatibility with JDK >8
  ),
)

def macroSettings: sbt.Def.SettingsDefinition = {
  libraryDependencies ++={
    Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % "0.10.3" cross CrossVersion.binary)
    ) ++ {
      // add scalamacros paradise only on <= 2.12; it's included in 2.13
      if (scalaVersion.value.startsWith("2.13")) Seq.empty else {
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
        )
      }
    }
  }
}

def gpbTestGenSettings(v: String) = inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings) ++ Seq(
  PB.protocVersion := s"-v$v",
  PB.targets in Test := Seq(
    PB.gens.java -> (sourceManaged in Test).value
  ),
  PB.includePaths in Compile ++= {
    Seq((baseDirectory in Test).value / "..")
  }
)

lazy val grpcTestGenSettings = gpbTestGenSettings(Versions.GPBv3) ++ Seq(
  grpcExePath := xsbti.api.SafeLazy.strict {
    val exe: File = (baseDirectory in Test).value / ".bin" / grpcExeFileName
    if (!exe.exists) {
      logger.info("gRPC protoc plugin (for Java) does not exist. Downloading")
      IO.transfer(grpcExeUrl.openStream(), exe)
      exe.setExecutable(true)
    } else {
      logger.debug("gRPC protoc plugin (for Java) exists")
    }
    exe
  },
  PB.protocOptions in Test ++= Seq(
    s"--plugin=protoc-gen-java_rpc=${grpcExePath.value.get}",
    s"--java_rpc_out=${(sourceManaged in Test).value.getAbsolutePath}"
  ),
)

/* --- --- --- --- ---  */

lazy val root = Project(id = "cactus",
  base = file(".")) settings (
    publish := {},
    crossScalaVersions := Nil,
  ) aggregate(commonModule, v2Module, v3Module, bytesV2Module, bytesV3Module, grpcCommonModule, grpcClientModule, grpcServerModule)

lazy val commonModule = Project(id = "common", base = file("./common")).settings(
  commonSettings,
  macroSettings,
  name := "cactus-common",
  libraryDependencies ++= Seq(
    "com.google.protobuf" % "protobuf-java" % Versions.gpb3Version % "optional",
    "com.google.protobuf" % "protobuf-java-util" % Versions.gpb3Version % "optional",

    "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
)

lazy val v2Module = Project(id = "gpbv2", base = file("./gpbv2")).settings(
  commonSettings,
  gpbTestGenSettings(Versions.GPBv2),
  name := "cactus-gpbv2",
  libraryDependencies ++= Seq(
    "com.google.protobuf" % "protobuf-java" % Versions.gpb2Version % "optional"
  )
).dependsOn(commonModule, bytesV2Module % "test")

lazy val v3Module = Project(id = "gpbv3", base = file("./gpbv3")).settings(
  commonSettings,
  gpbTestGenSettings(Versions.GPBv3),
  name := "cactus-gpbv3",
  libraryDependencies ++= Seq(
    "com.google.protobuf" % "protobuf-java" % Versions.gpb3Version,
    "com.google.protobuf" % "protobuf-java-util" % Versions.gpb3Version
  )
).dependsOn(commonModule)

lazy val bytesV2Module = Project(id = "bytes-gpbv2", base = file("./bytes-gpbv2")).settings(
  commonSettings,
  name := "cactus-bytes-gpbv2",
  libraryDependencies ++= Seq(
    "com.avast.bytes" % "bytes-gpb" % Versions.bytesVersion
  )
).dependsOn(commonModule)

lazy val bytesV3Module = Project(id = "bytes-gpbv3", base = file("./bytes-gpbv3")).settings(
  commonSettings,
  name := "cactus-bytes-gpbv3",
  libraryDependencies ++= Seq(
    "com.avast.bytes" % "bytes-gpb" % Versions.bytesVersion
  )
).dependsOn(v3Module)

lazy val grpcCommonModule = Project(id = "grpc-common", base = file("./grpc-common")).settings(
  commonSettings,
  macroSettings,
  name := "cactus-grpc-common",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "2.0.0",
    "io.grpc" % "grpc-protobuf" % Versions.grpcVersion,
    "io.grpc" % "grpc-stub" % Versions.grpcVersion % "test",
    "io.grpc" % "grpc-services" % Versions.grpcVersion % "test"
  )
).dependsOn(v3Module)

lazy val grpcClientModule = Project(id = "grpc-client", base = file("./grpc-client")).settings(
  commonSettings,
  macroSettings,
  grpcTestGenSettings,
  name := "cactus-grpc-client",
  libraryDependencies ++= Seq(
    "io.grpc" % "grpc-stub" % Versions.grpcVersion,
    "io.grpc" % "grpc-services" % Versions.grpcVersion % "test",
    "io.monix" %% "monix" % "3.0.0" % "test"
  )
).dependsOn(grpcCommonModule)

lazy val grpcServerModule = Project(id = "grpc-server", base = file("./grpc-server")).settings(
  commonSettings,
  macroSettings,
  grpcTestGenSettings,
  name := "cactus-grpc-server",
  libraryDependencies ++= Seq(
    "io.grpc" % "grpc-services" % Versions.grpcVersion,
    "io.grpc" % "grpc-stub" % Versions.grpcVersion % "test",
    "io.monix" %% "monix" % "3.0.0" % "test"
  )
).dependsOn(grpcCommonModule)

/* --- --- --- --- ---  */

def grpcExeFileName: String = {
  val os = if (scala.util.Properties.isMac) {
    "osx-x86_64"
  } else if (scala.util.Properties.isWin) {
    "windows-x86_64"
  } else {
    "linux-x86_64"
  }
  s"$grpcArtifactId-${Versions.grpcVersion}-$os.exe"
}

val grpcArtifactId = "protoc-gen-grpc-java"
val grpcExeUrl = url(s"http://repo1.maven.org/maven2/io/grpc/$grpcArtifactId/${Versions.grpcVersion}/$grpcExeFileName")
val grpcExePath = SettingKey[xsbti.api.Lazy[File]]("grpcExePath")
