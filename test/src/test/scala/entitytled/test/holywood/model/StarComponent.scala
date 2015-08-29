package entitytled.test.holywood.model

import entitytled.Entitytled

trait StarComponent {
  self: Entitytled with DirectorComponent with MovieComponent =>

  import driver.api._

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

    def * = (id.?, name, age) <> ((Star.apply _).tupled, Star.unapply)
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
