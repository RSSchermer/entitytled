
import org.typelevel.sbt.ReleaseSeries
import org.typelevel.sbt.Version._

TypelevelKeys.series in ThisBuild := ReleaseSeries(0,2)

TypelevelKeys.relativeVersion in ThisBuild := Relative(1,Final)

TypelevelKeys.lastRelease in ThisBuild := Relative(0,Final)
