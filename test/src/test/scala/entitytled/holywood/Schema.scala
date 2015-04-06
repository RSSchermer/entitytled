package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class Directors(tag: Tag) extends EntityTable[Director, DirectorID](tag, "DIRECTORS") {
  def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)

  def * = (id.?, name) <> ((Director.apply _).tupled, Director.unapply)
}

class Movies(tag: Tag) extends EntityTable[Movie, MovieID](tag, "MOVIES") {
  def id = column[MovieID]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)
  def directorID = column[DirectorID]("director_id", O.NotNull)

  def * = (id.?, title, directorID) <> ((Movie.apply _).tupled, Movie.unapply)

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

class Stars(tag: Tag) extends EntityTable[Star, StarID](tag, "STARS") {
  def id = column[StarID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name, age) <> ((Star.apply _).tupled, Star.unapply)
}
