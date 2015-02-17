name := "entitytled"

lazy val commonSettings = Seq(
  organization := "com.github.rsschermer",
  version := "0.1.0",
  scalaVersion := "2.11.4",
  crossScalaVersions := Seq("2.10.4", "2.11.5"),
  scalacOptions ++= Seq("-feature")
)

val slick         = "com.typesafe.slick"          %%  "slick"           % "2.1.0"

val monocleCore   = "com.github.julien-truffaut"  %%  "monocle-core"    % "1.0.0-M1"

val monocleMacros = "com.github.julien-truffaut"  %%  "monocle-macro"   % "1.0.0-M1"

val scalaTest     = "org.scalatest"               %%  "scalatest"       % "2.2.1"     % "test"

val h2database    = "com.h2database"              %   "h2"              % "1.4.181"   % "test"

val logback       = "ch.qos.logback"              %   "logback-classic" % "0.9.28"    % "test"

lazy val root = (project in file(".")).
  aggregate(core, test)

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      slick,
      monocleCore,
      monocleMacros
    )
  )

lazy val test = (project in file("test")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      slick,
      h2database,
      scalaTest,
      logback
    )
  ).
  dependsOn(core)
