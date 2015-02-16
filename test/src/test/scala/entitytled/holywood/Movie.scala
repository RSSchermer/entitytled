package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class MovieID(value: Long) extends MappedTo[Long]

case class Movie(
    id: Option[MovieID],
    title: String,
    directorID: DirectorID,
    director: One[Movie, Director],
    stars: Many[Movie, Star] = ManyFetched(Movie.stars))
  extends Entity { type IdType = MovieID }

object Movie extends EntityRepository[Movies, Movie] with EntityCompanion[Movies, Movie] {
  val query = TableQuery[Movies]

  val stars = toManyThrough[Stars, MoviesStars, Star](
    TableQuery[MoviesStars] innerJoin TableQuery[Stars] on(_.starID === _.id),
    _.id === _._1.movieID,
    lenser(_.stars)
  )

  val director = toOne[Directors, Director](TableQuery[Directors], _.directorID === _.id, lenser(_.director))
}
