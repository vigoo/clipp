import xerial.sbt.Sonatype._

name := "clipp"

dynverSonatypeSnapshots in ThisBuild := true

val scala212 = "2.12.10"
val scala213 = "2.13.1"

val scalacOptions212 = Seq("-Ypartial-unification", "-deprecation")
val scalacOptions213 = Seq("-deprecation")

lazy val commonSettings =
  Seq(
    scalaVersion := scala213,
    crossScalaVersions := List(scala212, scala213),

    organization := "io.github.vigoo",

    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      "org.typelevel" %% "cats-free" % "2.1.1",

      "org.atnos" %% "eff" % "5.9.0",
    ),

    coverageEnabled in(Test, compile) := true,
    coverageEnabled in(Compile, compile) := false,

    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => scalacOptions212
      case Some((2, 13)) => scalacOptions213
      case _ => Nil
    }),

    // Publishing

    publishMavenStyle := true,

    licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),

    publishTo := sonatypePublishTo.value,
    sonatypeProjectHosting := Some(GitHubHosting("vigoo", "clipp", "daniel.vigovszky@gmail.com")),

    developers := List(
      Developer(id = "vigoo", name = "Daniel Vigovszky", email = "daniel.vigovszky@gmail.com", url = url("https://vigoo.github.io"))
    ),

    credentials ++=
      (for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield
        Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password)).toSeq
  )

lazy val root = Project("clipp", file(".")).settings(commonSettings).settings(
  publishArtifact := false
) aggregate(core, zio, catsEffect)

lazy val core = Project("clipp-core", file("clipp-core")).settings(commonSettings).settings(
  description := "Clipp core",

  libraryDependencies ++= Seq(
    "org.specs2" %% "specs2-core" % "4.9.2" % "test"
  )
)

lazy val zio = Project("clipp-zio", file("clipp-zio")).settings(commonSettings).settings(
  description := "Clipp ZIO interface",

  libraryDependencies ++= Seq(
    "dev.zio" %% "zio" % "1.0.0-RC20",
    "dev.zio" %% "zio-test" % "1.0.0-RC20" % Test,
    "dev.zio" %% "zio-test-sbt" % "1.0.0-RC20" % Test
  ),

  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
).dependsOn(core)

lazy val catsEffect = Project("clipp-cats-effect", file("clipp-cats-effect")).settings(commonSettings).settings(
  description := "Clipp Cats-Effect interface",

  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "2.1.3",
    "org.specs2" %% "specs2-core" % "4.10.0" % "test"
  )
).dependsOn(core)
