package entitytled.test.holywood.model

import entitytled.Entitytled

trait MovieComponent {
  self: Entitytled with DirectorComponent with StarComponent =>

  import driver.api._

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
    val stars = toManyThrough[Stars, MoviesStars, Star]
    val director = toOne[Directors, Director]
  }

  class Movies(tag: Tag) extends EntityTable[Movie, MovieID](tag, "MOVIES") {
    def id = column[MovieID]("id", O.PrimaryKey, O.AutoInc)
    def title = column[String]("title")
    def directorID = column[DirectorID]("director_id")

    def * = (id.?, title, directorID) <>((Movie.apply _).tupled, Movie.unapply)

    def director = foreignKey("MOVIES_DIRECTOR_FK", directorID, TableQuery[Directors])(_.id)
  }
}
