package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class MovieID(value: Long) extends MappedTo[Long]

case class Movie(
    id: Option[MovieID],
    title: String,
    directorID: DirectorID)(implicit includes: Includes[Movie])
  extends Entity[Movie, MovieID]
{
  val director = one(Movie.director)
  val stars = many(Movie.stars)
}

object Movie extends EntityCompanion[Movies, Movie, MovieID] {
  val query = TableQuery[Movies]

  val stars = toManyThrough[Stars, MoviesStars, Star](
    TableQuery[MoviesStars] innerJoin TableQuery[Stars] on(_.starID === _.id),
    _.id === _._1.movieID)

  val director = toOne[Directors, Director](
    TableQuery[Directors],
    _.directorID === _.id)
}
