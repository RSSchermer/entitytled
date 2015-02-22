package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._
import monocle.Lens

case class MovieID(value: Long) extends MappedTo[Long]

case class Movie(
    id: Option[MovieID],
    title: String,
    directorID: DirectorID)
    (implicit val director: One[Movie, Director] = OneUnfetched[Movie, Director](Movie.director, id),
              val stars: Many[Movie, Star] = ManyUnfetched[Movie, Star](Movie.stars, id))
  extends Entity { type IdType = MovieID }

object Movie extends EntityRepository[Movies, Movie] with EntityCompanion[Movies, Movie] {
  val query = TableQuery[Movies]

  val stars = toManyThrough[Stars, MoviesStars, Star](
    TableQuery[MoviesStars] innerJoin TableQuery[Stars] on(_.starID === _.id),
    _.id === _._1.movieID,
    Lens[Movie, Many[Movie, Star]](_.stars)(s => m => m.copy()(stars = s, director = m.director)))

  val director = toOne[Directors, Director](
    TableQuery[Directors],
    _.directorID === _.id,
    Lens[Movie, One[Movie, Director]](_.director)(d => m => m.copy()(director = d, stars = m.stars)))
}
