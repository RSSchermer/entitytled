package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class StarID(value: Long) extends MappedTo[Long]

case class Star(
    id: Option[StarID],
    name: String,
    age: Int)(implicit includes: Includes[Star])
  extends Entity[Star]
{
  type IdType = StarID

  val movies = many(Star.movies)
}

object Star extends EntityCompanion[Stars, Star] {
  val query = TableQuery[Stars]

  val movies = toManyThrough[Movies, MoviesStars, Movie](
    TableQuery[MoviesStars] innerJoin TableQuery[Movies] on(_.movieID === _.id),
    _.id === _._1.starID)
}
