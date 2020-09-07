import microsites.ConfigYml
import xerial.sbt.Sonatype._

name := "clipp"

dynverSonatypeSnapshots in ThisBuild := true

val scala212 = "2.12.12"
val scala213 = "2.13.3"

val scalacOptions212 = Seq("-Ypartial-unification", "-deprecation")
val scalacOptions213 = Seq("-deprecation")

lazy val commonSettings =
  Seq(
    scalaVersion := scala213,
    crossScalaVersions := List(scala212, scala213),

    organization := "io.github.vigoo",

    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.2.0",
      "org.typelevel" %% "cats-free" % "2.2.0",

      "org.atnos" %% "eff" % "5.11.0",
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

    publishTo := sonatypePublishToBundle.value,
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
    "dev.zio" %% "zio" % "1.0.1",
    "dev.zio" %% "zio-test" % "1.0.1" % Test,
    "dev.zio" %% "zio-test-sbt" % "1.0.1" % Test
  ),

  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
).dependsOn(core)

lazy val catsEffect = Project("clipp-cats-effect", file("clipp-cats-effect")).settings(commonSettings).settings(
  description := "Clipp Cats-Effect interface",

  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "2.2.0",
    "org.specs2" %% "specs2-core" % "4.10.3" % "test"
  )
).dependsOn(core)

lazy val docs = project
  .settings(commonSettings)
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .enablePlugins(MicrositesPlugin)
  .settings(
    name := "clipp",
    description := "Functional command line argument parser and usage info generator for Scala",
    publishArtifact := false,
    siteSubdirName in ScalaUnidoc := "api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    unidocProjectFilter in ( ScalaUnidoc, unidoc ) := inAnyProject,
    git.remoteRepo := "git@github.com:vigoo/clipp.git",
    micrositeUrl := "https://vigoo.github.io",
    micrositeBaseUrl := "/clipp",
    micrositeHomepage := "https://vigoo.github.io/clipp/",
    micrositeDocumentationUrl := "/clipp/docs",
    micrositeAuthor := "Daniel Vigovszky",
    micrositeTwitterCreator := "@dvigovszky",
    micrositeGithubOwner := "vigoo",
    micrositeGithubRepo := "clipp",
    micrositeGitterChannel := false,
    micrositeDataDirectory := file("docs/src/microsite/data"),
    micrositeStaticDirectory := file("docs/src/microsite/static"),
    micrositeImgDirectory := file("docs/src/microsite/img"),
    micrositeCssDirectory := file("docs/src/microsite/styles"),
    micrositeSassDirectory := file("docs/src/microsite/partials"),
    micrositeJsDirectory := file("docs/src/microsite/scripts"),
    micrositeTheme := "light",
    micrositeHighlightLanguages ++= Seq("scala", "sbt"),
    micrositeConfigYaml := ConfigYml(
      yamlCustomProperties = Map(
        "url" -> "https://vigoo.github.io",
        "plugins" -> List("jemoji", "jekyll-sitemap")
      )
    ),
    //micrositeAnalyticsToken := "UA-56320875-2",
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.txt" | "*.xml" | "*.svg",
  )
  .dependsOn(core, catsEffect, zio)

// Temporary fix to avoid including mdoc in the published POM

import scala.xml.{Node => XmlNode, NodeSeq => XmlNodeSeq, _}
import scala.xml.transform.{RewriteRule, RuleTransformer}

// skip dependency elements with a scope
pomPostProcess := { (node: XmlNode) =>
  new RuleTransformer(new RewriteRule {
    override def transform(node: XmlNode): XmlNodeSeq = node match {
      case e: Elem if e.label == "dependency" && e.child.exists(child => child.label == "artifactId" && child.text.startsWith("mdoc_")) =>
        val organization = e.child.filter(_.label == "groupId").flatMap(_.text).mkString
        val artifact = e.child.filter(_.label == "artifactId").flatMap(_.text).mkString
        val version = e.child.filter(_.label == "version").flatMap(_.text).mkString
        Comment(s"dependency $organization#$artifact;$version has been omitted")
      case _ => node
    }
  }).transform(node).head
}