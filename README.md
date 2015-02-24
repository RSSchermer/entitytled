# Entitytled

Entitytled is a data access and persistence library build on top of the amazing 
[Scala Slick](http://slick.typesafe.com/) library.

[![Build Status](https://travis-ci.org/RSSchermer/entitytled.svg?branch=master)](https://travis-ci.org/RSSchermer/entitytled)

NOTE: Entitytled is currently still under development and may be subject to
large breaking changes.

## Simple Example

```scala
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

// Schema:
// Defining the schema is largely the same as for plain Slick, except that we
// extend EntityTable instead of Table; EntityTable's must define an id column.
class Directors(tag: Tag) extends EntityTable[Director](tag, "DIRECTORS") {
  def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)

  def * = (id.?, name) <> ((Director.apply _).tupled, Director.unapply)
}

class Movies(tag: Tag) extends EntityTable[Movie](tag, "MOVIES") {
  def id = column[MovieID]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)
  def directorID = column[DirectorID]("director_id", O.NotNull)

  def * = (id.?, title, directorID) <> ((Movie.apply _).tupled, Movie.unapply)

  def director = foreignKey("MOVIES_DIRECTOR_FK", directorID, TableQuery[Directors])(_.id)
}

// Model:
case class DirectorID(value: Long) extends MappedTo[Long]

case class Director(
    id: Option[DirectorID],
    name: String)(implicit includes: Includes[Director])
  extends Entity[Director]
{
  type IdType = DirectorID

  val movies = many(Director.movies)
}

object Director extends EntityCompanion[Directors, Director] {
  val query = TableQuery[Directors]

  val movies = toMany[Movies, Movie](
    toQuery       = TableQuery[Movies],
    joinCondition = _.id === _.directorID)
}

case class MovieID(value: Long) extends MappedTo[Long]

case class Movie(
    id: Option[MovieID],
    title: String,
    directorID: DirectorID)(implicit includes: Includes[Movie])
  extends Entity[Movie]
{
  type IdType = MovieID

  val director = one(Movie.director)
}

object Movie extends EntityCompanion[Movies, Movie] {
  val query = TableQuery[Movies]

  val director = toOne[Directors, Director](
    toQuery       = TableQuery[Directors],
    joinCondition = _.directorID === _.id)
}
```

We could now query for entities as follows:

```scala
// Find a movie by its ID:
val movie = Movie.find(MovieID(8)) // Option[Movie] The Usual Suspects

// Get its director through explicit late loading:
movie.director.getOrFetch // Option[Director] Bryan Singer

// You could also have side-loaded the director to limit the number of queries:
Movie.include(Movie.director).list.foreach {
  // Only 2 queries instead of n + 1 (where n is the number of movies in the list)
  println _.director.getOrFetch
}

// You can side-load multiple relationships at once:
Movie.include(Movie.director, Movie.stars).list

// You can also nest side-load on other side-loads (to arbitrary depth):
val director = Director.include(Director.movies.include(Movie.stars))
  .find(DirectorID(3)).get
director.movies.getOrFetch.flatMap(_.stars.getOrFetch).foreach { println }

// You can build more complex entity collections with sorting, filtering, etc.:
Director.filter(_.age >= 65).sortBy(_.name.asc).drop(100).take(10)
  .include(Director.movies).list
```

We can also create, update and delete entities:

```scala
// Create
Movie.insert(Movie(None, "Goodfellas", DirectorID(12)))

// Update
val movie = Movie.filter(_.title === "The Matrix 2").firstOption.get
Movie.update(movie.copy(title = "The Matrix Reloaded"))

// Delete
Movie.delete(MovieID(10))
```

(More detailed documentation is coming. For now, you might want to take a look
at the [tests](/test/src/test/scala/entitytled) for a more elaborate example.)

## License

MIT
