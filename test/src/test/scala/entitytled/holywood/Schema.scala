package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class Directors(tag: Tag) extends EntityTable[Director](tag, "DIRECTORS") {
  def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)

  def * = (id.?, name) <> (mapRecord, unmapRecord)

  def mapRecord(t: (Option[DirectorID], String)): Director =
    Director(t._1, t._2, ManyUnfetched[Director, Movie](Director.movies, t._1))

  def unmapRecord(d: Director): Option[(Option[DirectorID], String)] =
    Some(d.id, d.name)
}

class Movies(tag: Tag) extends EntityTable[Movie](tag, "MOVIES") {
  def id = column[MovieID]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)
  def directorID = column[DirectorID]("director_id", O.NotNull)

  def * = (id.?, title, directorID) <> (mapRecord, unmapRecord)

  def mapRecord(t: (Option[MovieID], String, DirectorID)): Movie =
    Movie(t._1, t._2, t._3, OneUnfetched[Movie, Director](Movie.director, t._1),
      ManyUnfetched[Movie, Star](Movie.stars, t._1))

  def unmapRecord(m: Movie): Option[(Option[MovieID], String, DirectorID)] =
    Some(m.id, m.title, m.directorID)

  def director = foreignKey("MOVIES_DIRECTOR_FK", directorID, TableQuery[Directors])(_.id)
}

class MoviesStars(tag: Tag) extends Table[(MovieID, StarID)](tag, "MOVIES_STARS") {
  def movieID = column[MovieID]("movie_id", O.NotNull)
  def starID = column[StarID]("star_id", O.NotNull)

  def * = (movieID, starID)

  def pk = primaryKey("MOVIES_STARS_PK", (movieID, starID))
  def movie = foreignKey("MOVIES_STARS_MOVIE_FK", movieID, TableQuery[Movies])(_.id)
  def star = foreignKey("MOVIES_STARS_STAR_FK", starID, TableQuery[Stars])(_.id)
}

class Stars(tag: Tag) extends EntityTable[Star](tag, "STARS") {
  def id = column[StarID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name, age) <> (mapRecord, unmapRecord)

  def mapRecord(t: (Option[StarID], String, Int)): Star =
    Star(t._1, t._2, t._3, ManyUnfetched[Star, Movie](Star.movies, t._1))

  def unmapRecord(d: Star): Option[(Option[StarID], String, Int)] =
    Some(d.id, d.name, d.age)
}
