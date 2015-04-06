package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class DirectorID(value: Long) extends MappedTo[Long]

case class Director(
    id: Option[DirectorID],
    name: String)(implicit includes: Includes[Director])
  extends Entity[Director, DirectorID]
{
  val movies = many(Director.movies)
}

object Director extends EntityCompanion[Directors, Director, DirectorID] {
  val query = TableQuery[Directors]

  val movies = toMany[Movies, Movie](
    TableQuery[Movies],
    _.id === _.directorID)
}
