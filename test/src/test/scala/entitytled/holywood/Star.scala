package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class StarID(value: Long) extends MappedTo[Long]

case class Star(
    id: Option[StarID],
    name: String,
    age: Int,
    movies: Many[Star, Movie] = ManyFetched(Star.movies))
  extends Entity { type IdType = StarID }

object Star extends EntityRepository[Stars, Star] with EntityCompanion[Stars, Star] {
  val query = TableQuery[Stars]

  val movies = toManyThrough[Movies, MoviesStars, Movie](
    TableQuery[MoviesStars] innerJoin TableQuery[Movies] on(_.movieID === _.id),
    _.id === _._1.starID,
    lenser(_.movies)
  )
}
