package entitytled.test.holywood

import entitytled.Entitytled

trait Model {
  self: Entitytled =>

  import driver.api._

  case class DirectorID(value: Long) extends MappedTo[Long]

  case class Director(
      id: Option[DirectorID],
      name: String)(implicit includes: Includes[Director])
    extends Entity[Director, DirectorID]
  {
    val movies = many(Director.movies)
    val stars = many(Director.stars)
  }

  object Director extends EntityCompanion[Directors, Director, DirectorID] {
    val movies = toMany[Movies, Movie]
    val stars = movies compose Movie.stars
  }

  class Directors(tag: Tag) extends EntityTable[Director, DirectorID](tag, "DIRECTORS") {
    def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")

    def * = (id.?, name) <>((Director.apply _).tupled, Director.unapply)
  }

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

  case class StarID(value: Long) extends MappedTo[Long]

  case class Star(
      id: Option[StarID],
      name: String,
      age: Int)(implicit includes: Includes[Star])
    extends Entity[Star, StarID]
  {
    val movies = many(Star.movies)
    val directors = many(Star.directors)
  }

  object Star extends EntityCompanion[Stars, Star, StarID] {
    val movies = toManyThrough[Movies, MoviesStars, Movie]
    val directors = movies compose Movie.director
  }

  class Stars(tag: Tag) extends EntityTable[Star, StarID](tag, "STARS") {
    def id = column[StarID]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def age = column[Int]("age")

    def * = (id.?, name, age) <>((Star.apply _).tupled, Star.unapply)
  }

  class MoviesStars(tag: Tag) extends Table[(MovieID, StarID)](tag, "MOVIES_STARS") {
    def movieID = column[MovieID]("movie_id")
    def starID = column[StarID]("star_id")

    def * = (movieID, starID)

    def pk = primaryKey("MOVIES_STARS_PK", (movieID, starID))
    def movie = foreignKey("MOVIES_STARS_MOVIE_FK", movieID, TableQuery[Movies])(_.id)
    def star = foreignKey("MOVIES_STARS_STAR_FK", starID, TableQuery[Stars])(_.id)
  }
}
