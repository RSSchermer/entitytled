package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._
import monocle._

case class DirectorID(value: Long) extends MappedTo[Long]

case class Director(
    id: Option[DirectorID],
    name: String)
    (implicit val movies: Many[Director, Movie] = ManyUnfetched[Director, Movie](Director.movies, id))
  extends Entity { type IdType = DirectorID }

object Director extends EntityRepository[Directors, Director] with EntityCompanion[Directors, Director] {
  val query = TableQuery[Directors]

  val movies = toMany[Movies, Movie](
    TableQuery[Movies],
    _.id === _.directorID,
    Lens[Director, Many[Director, Movie]](_.movies)( m => e => e.copy()(movies = m))
  )
}
