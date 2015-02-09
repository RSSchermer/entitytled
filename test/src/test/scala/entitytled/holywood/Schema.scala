package entitytled.holywood

import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class Movies(tag: Tag) extends EntityTable[Movie](tag, "MOVIES") {
  def id = column[MovieID]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)

  def * = (id.?, title) <> (mapRecord, unmapRecord)

  def mapRecord(t: (Option[MovieID], String)): Movie =
    Movie(t._1, t._2, ManyUnfetched[Movie, Star](Movie.stars, t._1))

  def unmapRecord(m: Movie): Option[(Option[MovieID], String)] =
    Some(m.id, m.title)
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
