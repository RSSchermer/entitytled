import sbt._
import Keys._

import xerial.sbt.Sonatype._
import org.typelevel.sbt.TypelevelPlugin._

object BuildSettings {
  import EntitytledPublishing._

  val buildScalaVersion = "2.11.6"

  val buildSettings = typelevelDefaultSettings ++ Seq(
    organization       := "com.github.rsschermer",
    scalaVersion       := buildScalaVersion,
    crossScalaVersions := Seq("2.10.5", "2.11.6"),
    scalacOptions      ++= Seq(
      "-feature",
      "-deprecation",
      "-Xlog-reflective-calls"
    ),
    resolvers          += Resolver.sonatypeRepo("releases"),
    resolvers          += Resolver.sonatypeRepo("snapshots")
  ) ++ publishSettings
}

object Dependencies {
  val slick         = "com.typesafe.slick"  %%  "slick"           % "3.0.0"
  val scalaz        = "org.scalaz"          %%  "scalaz-core"     % "7.1.2"
  val scalaTest     = "org.scalatest"       %%  "scalatest"       % "2.2.1"     % "test"
  val h2database    = "com.h2database"      %   "h2"              % "1.4.181"   % "test"
  val logback       = "ch.qos.logback"      %   "logback-classic" % "0.9.28"    % "test"
}

object EntitytledBuild extends Build {
  import BuildSettings._
  import Dependencies._

  lazy val root: Project = Project(
    "entitytled",
    file("."),
    settings = buildSettings ++ Seq(
      publishArtifact := false)
  ) aggregate(core, test)

  lazy val core: Project = Project(
    "entitytled-core",
    file("core"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(slick, scalaz, "org.scala-lang" % "scala-reflect" % scalaVersion.value),
      libraryDependencies := {
        CrossVersion.partialVersion(scalaVersion.value) match {
          // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
          case Some((2, scalaMajor)) if scalaMajor >= 11 =>
            libraryDependencies.value
          // in Scala 2.10, quasiquotes are provided by macro paradise
          case Some((2, 10)) =>
            libraryDependencies.value ++ Seq(
              compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
              "org.scalamacros" %% "quasiquotes" % "2.0.1" cross CrossVersion.binary
            )
        }
      }
    )
  )

  lazy val test: Project = Project(
    "entitytled-test",
    file("test"),
    settings = buildSettings ++ Seq(
      publishArtifact      := false,
      libraryDependencies ++= Seq(h2database, scalaTest, logback)
    )
  ) dependsOn(core)
}

object EntitytledPublishing  {
  lazy val publishSettings: Seq[Setting[_]] = Seq(
    pomExtra := {
      <url>https://github.com/RSSchermer/entitytled</url>
        <licenses>
          <license>
            <name>MIT</name>
            <url>http://opensource.org/licenses/MIT</url>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:RSSchermer/entitytled.git</url>
          <connection>scm:git:git@github.com:RSSchermer/entitytled.git</connection>
        </scm>
        <developers>
          <developer>
            <id>rsschermer</id>
            <name>R.S.Schermer</name>
          </developer>
        </developers>
    }
  ) ++ sonatypeSettings
}
