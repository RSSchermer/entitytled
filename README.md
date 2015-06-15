# Entitytled

Entitytled is an ORM-like data access and persistence library build on top of 
the amazing [Scala Slick](http://slick.typesafe.com/) library. Entitytled 
introduces to concepts "entity" and "relationship" with the aims of bringing 
more structure and consistency, and eliminating boilerplate.

[![Build Status](https://travis-ci.org/RSSchermer/entitytled.svg?branch=master)](https://travis-ci.org/RSSchermer/entitytled)

Notable features include:

- Type safety: build on top of the type safe Slick library.
- Object Oriented-style relationship access: navigate relationships as fields
  (`director.movies`) instead of relying on tuples (`directorWithMovies._2`).
- Relationship eager-loading: eager-loading one or more relationships or
  arbitrarily nested relationships is supported.
- Basic CRUD methods for inserting, finding, updating and deleting entities.

The rest of this readme is a usage guide. The examples used in the guide are
based on the tests for this library, so if at any point you get confused, it 
may help to [have a look at the tests](test/src/test/scala/entitytled).

## Table of contents

- [Artifact installation](#artifact-installation)
- [Defining an Entity type](#defining-an-entity-type)
  - [Importing a profile](#importing-a-profile)
  - [Defining the Entity type](#defining-the-entity-type)
  - [Defining the companion object](#defining-the-companion-object)
  - [Safer IDs](#safer-ids)
- [Defining direct relationships](#defining-direct-relationships)
  - [Direct 'to one' relationships](#direct-to-one-relationships)
  - [Direct 'to many' relationships](#direct-to-many-relationships)
- [Defining indirect relationships (many-to-many)](#defining-indirect-relationships-many-to-many)
- [Querying an entity set](#querying-an-entity-set)
  - [Building read actions](#building-read-actions)
  - [Creating an insert action](#creating-an-insert-action)
  - [Creating an update action](#creating-an-update-action)
  - [Creating a delete action](#creating-a-delete-action)
- [Navigating relationship values](#navigating-relationship-values)
- [Play Framework](#play-framework)
  - [Activator Template](#activator-template)
  - [Real world example](#real-world-example)

## Artifact installation

The Entitytled artifact is available on Sonatype's central repository and is
cross-build for Scala 2.10 and Scala 2.11.

To make SBT automatically use the correct build based on your project's Scala
version, add the following to your build file:

```sbt
libraryDependencies += "com.github.rsschermer" %% "entitytled-core" % "0.5.0"
```

To use the 2.10 build explicitly add:

```sbt
libraryDependencies += "com.github.rsschermer" % "entitytled-core_2.10" % "0.5.0"
```

To use the 2.11 build explicitly add:

```sbt
libraryDependencies += "com.github.rsschermer" % "entitytled-core_2.11" % "0.5.0"
```

## Defining an Entity type

In Entitytled, entity types are case classes which extend the `Entity` base class.
They need to be accompanied by an `EntityTable` definition, which describes the
schema for the entity. They may additionally by accompanied by an 
`EntityCompanion` object, which provides a default set of methods for querying
and persisting entities.

This example defines a Director entity, which models a movie director:

```scala
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

case class Director(
    id: Option[Long],
    name: String,
    age: Int)
  extends Entity[Director, Long]

object Director extends EntityCompanion[Directors, Director, Long]

class Directors(tag: Tag) extends EntityTable[Director, Long](tag, "DIRECTORS") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name) <> ((Director.apply _).tupled, Director.unapply)
}
```

Lets break this example up and take a closer look at the individual pieces.

### Importing a profile

Slick provides drivers for multiple databases. Entitytled can be used with any
driver that implements `scala.slick.driver.JdbcProfile`. The Director example
uses a predefined profile for the [H2 database](http://www.h2database.com):

```scala
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._
```

The first line imports Entitytled's functionality for Slick's H2 driver and the 
second line imports Slick's own functionality for the H2 driver. (Note: 
Entitytled also supports dependency injection via the cake pattern. However, 
these examples will use hard-coded import statements instead, as the cake 
pattern adds a certain amount of complexity.) 

Predefined profiles exist for several other databases:

- `entitytled.profile.PostgresProfile` for use with [PostgreSQL](http://www.postgresql.org).
- `entitytled.profile.MySQLProfile` for use with [MySQL](http://www.mysql.com).
- `entitytled.profile.SQLiteProfile` for use with [SQLite](https://sqlite.org).
- `entitytled.profile.DerbyProfile` for use with [Apache Derby](https://db.apache.org/derby).
- `entitytled.profile.HsqldbProfile` for use with [HyperSQL DataBase](http://hsqldb.org).

To use e.g. PostgreSQL instead of H2 use:

```scala
import entitytled.profile.PostgresProfile._
import entitytled.profile.PostgresProfile.driver.simple._
```

You may also define your own profile for a driver that is not included in the
standard distribution of Slick:

```scala
package models.meta

import entitytled.Entitytled
import my.driver.MyDriver

trait MyProfile extends Entitytled {
  val driver: MyDriver = MyDriver
}

object MyProfile extends MyProfile
```

And use it like this:

```scala
import models.meta.MyProfile._
import models.meta.MyProfile.driver.simple._
```

### Defining the Entity type

The second segment of interest in the Director example is the definition of 
the `Director` case class:

```scala
case class Director(
    id: Option[Long],
    name: String,
    age: Int)
  extends Entity[Director, Long]
```

The `Director` case class extends the `Entity` base class, which takes two type
parameters: the `Director` type itself (a self bound), and the type of its ID:

```scala
extends Entity[Director, Long]
```

The `id` field then needs to be of type `Option[IdType]`, which in this case
means it needs to be of type `Option[Long]`.

### Defining the companion object

Next the Director example defines a `Director` companion object for the
`Director` case class: 

```scala
object Director extends EntityCompanion[Directors, Director, Long]
```

This companion object will be our entry point for querying and persisting 
directors. It extends `EntityCompanion` which takes the table type `Directors`
and the entity type `Director`, and the id type `Long` as type parameters.

### Defining the table type

The final part of the Director example consists of the definition of the
`Directors` table:

```scala
class Directors(tag: Tag) extends EntityTable[Director, Long](tag, "DIRECTORS") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name, age) <> ((Director.apply _).tupled, Director.unapply)
}
```

Table definition is essentially the same as it is in Slick and you can find
more details in [Slick's documentation on the subject](http://slick.typesafe.com/doc/3.0.0/schemas.html).
Instead of extending a regular `Table`, a table definition for an entity needs
to extend the `EntityTable` base class. The `EntityTable` base class takes two 
type parameters, the entity type and the ID type, and requires that you at least 
define an `id` method that returns a value of type `Column[IdType]`:

```scala
def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
```

Usually the `id` column is also the primary key of an entity table.

### Safer IDs

To keep this example simple, the ID type was specified simply as `Long`. 
However, if we'd also have a `Producer` entity type, which also specified its ID
type as a simple `Long`, we could be at risk of mixing up our director IDs with 
our producer IDs. Slick also supports using more precise types that wrap a 
primitive type. We could make use of this to make our `id` field a bit safer:

```scala
case class DirectorID(value: Long) extends MappedTo[Long]
```

We'd then have to update our `Director` entity definition as follows:

```scala
case class Director(
    id: Option[DirectorID],
    name: String,
    age: Int)
  extends Entity[Director, DirectorID]

object Director extends EntityCompanion[Directors, Director, DirectorID]

class Directors(tag: Tag) extends EntityTable[Director, DirectorID](tag, "DIRECTORS") {
  def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name) <> ((Director.apply _).tupled, Director.unapply)
}
```

The [Holywood example used for testing](test/src/test/scala/entitytled/holywood)
also uses this safer way of handling IDs.

## Defining direct relationships

Direct relationships are relationships that involve at most two tables: one 
table for the owner entity type and one table for the target relation. The 
relationship is defined by a foreign key, either on the owner or the target.

### Direct 'to one' relationships

Let's define a `Movie` entity type which has a 'to one' relationship with the 
`Director` entity type:

```scala
case class Movie(
    id: Option[Long],
    title: String,
    directorID: Long)(implicit includes: Includes[Movie])
  extends Entity[Movie, Long]
{
  val director = one(Movie.director)
}

object Movie extends EntityCompanion[Movies, Movie, Long] {
  val director = toOne[Directors, Director]
}

class Movies(tag: Tag) extends EntityTable[Movie, Long](tag, "MOVIES") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)
  def directorID = column[Long]("director_id", O.NotNull)

  def * = (id.?, title, directorID) <> ((Movie.apply _).tupled, Movie.unapply)

  def director = foreignKey("MOVIES_DIRECTOR_FK", directorID, TableQuery[Directors])(_.id)
}
```

The `Movie` 'to one' `Director` relationship in this example is a direct
relationship; the foreign key in this example is the `directorID` field on the
`Movie` entity type. We've also specified a foreign key constraint in our table
definition (see [Slick's documentation for more details on table definition](http://slick.typesafe.com/doc/2.1.0/schemas.html)):

```scala
def director = foreignKey("MOVIES_DIRECTOR_FK", directorID, TableQuery[Directors])(_.id)
```

Appart from having a foreign key field, `Movie` differs in some other ways from
the simpler `Director` example that did not have any relationships. The first is 
the addition of an implicit `includes` parameter to the case class constructor:

```scala
(implicit includes: Includes[Movie])
```

This is to support eager-loading (a topic discussed in more detail in the
"Querying an Entity set" section of this guide). It does not have to be named
`includes`, but it does need to be of type `Includes[EntityType]`, which in this 
case means it has to be of type `Includes[Movie]`.

The second difference is the addition of a `director` field in the case class
body:

```scala
val director = one(Movie.director)
```

Let's skip that difference for a second and look at the third difference first,
the addition of a `director` field on the companion object:

```scala
val director = toOne[Directors, Director]
```

This defines the relationship by calling the `toOne` function. The `toOne`
function takes two type parameters: the target table type (`Directors` in this 
case) and the target type (`Director` in this case). You can also specify two
optional parameters:

- `toQuery`: a query identifying the set of all target relations of the target
  type that could possibly be related to a movie. In this case the target type
  is `Director`, so the `toQuery` must identify a set of directors.
- `joinCondition`: the condition that joins a specific owner instance to the
  subset of relations identified by `toQuery` that belong to it. In this case
  `_.directorID === _.id` means that a certain director belongs to a movie if
  his `id` equals the `directorID` of that movie (the foreign key constraint).
  If you don't specify this parameter, Entitytled will attempt to infer a join
  condition at compile time, based on the foreign keys you defined on the owner
  table type and the target table type. For Entitytled to be able to do this,
  there must be exactly one candidate foreign key (a foreign from the owner 
  table to the target table, or from the target table to the owner table). If
  there's more than one candidate, or if you did not define any candidate
  foreign keys, you will get a compiler error. You can always resolve this error
  by providing a join condition explicitly.
  
The example just uses the defaults for these parameters, which is essentially
equivalent to:

```scala
val director = toOne[Directors, Director](
  toQuery       = TableQuery[Directors],
  joinCondition = (m: Movies, d: Directors) => m.directorID === d.id
)
```

Now, let's take another look at the `director` field on our `Movie` case class:

```scala
val director = one(Movie.director)
```

This calls the `one` function, which takes the relationship we defined in the 
companion object as an argument. If we have a specific movie instance, this
field will represent the related director:

```scala
vertigo.director // Alfred Hitchcock
```

Navigating relationships is described in more detail in its own section.

### Direct 'to many' relationships

Direct 'to many' relationships are very similar to 'to one' relationships.
Here's a modified version of the simple `Director` example, except now with a
'to many' relationship to the `Movie` type:

```scala
case class Director(
    id: Option[Long],
    name: String,
    age: Int)(implicit includes: Includes[Director])
  extends Entity[Director, Long]
{
  val movies = many(Director.movies)
}

object Director extends EntityCompanion[Directors, Director, Long] {  
  val movies = toMany[Movies, Movie]
}

class Directors(tag: Tag) extends EntityTable[Director, Long](tag, "DIRECTORS") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def age = column[Int]("age", O.NotNull)

  def * = (id.?, name, age) <> ((Director.apply _).tupled, Director.unapply)
}
```

There's no foreign key field here, that's already covered by the foreign key
on the `Movie` type. Just as with the `Movie` type, an implicit `includes`
constructor argument was added to the case class to enable eager-loading. 

We've added a `movies` field to case class body, except this time it calls the
`many` function instead of the `one` function:

```scala
val movies = many(Director.movies)
```
 
`many` works exactly the same as `one`, except it is used for representing a 
related collection, instead of a single related instance.

The last change made, is the addition of the `movies` field on the companion
object:

```scala
val movies = toMany[Movies, Movie]
```

This is basically the inverse of the `director` field on the `Movie` companion
object described in the previous section. Together, this 'to many' relationship
definition and the 'to one' relationship on the `Movie` companion object, form 
the full '`Director` one-to-many `Movie`' relationship.

One thing better illustrated for a 'to many' relationship (but technically also
possible for a 'to one' relationship) is that we can further constrain the set
of possible related instances via the optional `toQuery` argument. We might for 
example define a relationship for only black-and-white movies:

```scala
val blackAndWhiteMovies = toMany[Movies, Movie](
  toQuery = TableQuery[Movies].filter(_.color === false)
)
```

## Defining indirect relationships (many-to-many)

Indirect relationships rely on a 'join table'. Instead of one of the tables for
the two related types defining a foreign key, another table is responsible for
recording the relationships between these types. This is typically how
'many-to-many' relationships are defined.

As an example we'll set a many-to-many relationship between the `Movie` type
used in the direct relationships example, and a new `Star` type, which describes
a movie star. This is what the join table looks like:

```scala
class MoviesStars(tag: Tag) extends Table[(Long, Long)](tag, "MOVIES_STARS") {
  def movieID = column[Long]("movie_id", O.NotNull)
  def starID = column[Long]("star_id", O.NotNull)

  def * = (movieID, starID)

  def pk = primaryKey("MOVIES_STARS_PK", (movieID, starID))
  def movie = foreignKey("MOVIES_STARS_MOVIE_FK", movieID, TableQuery[Movies])(_.id)
  def star = foreignKey("MOVIES_STARS_STAR_FK", starID, TableQuery[Stars])(_.id)
}
```

Note that this does not extend `EntityTable`, it just extends Slick's plain old
`Table`. That's because the rows in this table do not represent entities, it
exists so we can  describe our many-to-many relationship. There are 2 foreign 
keys, one for the star's ID and one for the movie's ID, and the 
`(movieID, StarID)` pair makes up the table's primary key.

Now for the `Movie` type:

```scala
case class Movie(
    id: Option[Long],
    title: String)(implicit includes: Includes[Movie])
  extends Entity[Movie, Long]
{
  val stars = many(Movie.stars)
}

object Movie extends EntityCompanion[Movies, Movie, Long] {  
  val stars = toManyThrough[Stars, MoviesStars, Star]
}

class Movies(tag: Tag) extends EntityTable[Movie, Long](tag, "MOVIES") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def title = column[String]("title", O.NotNull)

  def * = (id.?, title) <> ((Movie.apply _).tupled, Movie.unapply)
}
```

It's very similar to the `Movie` type definition in the Direct relationships
example. The `Director` relationship stuff (the foreign key and `director` fields
on the case class and companion object) has been removed to keep it simple.

We've added a `stars` field to the case class body so we can navigate the
relationship again:

```scala
val stars = many(Movie.stars)
```

This works exactly the same for indirect 'to many' relationships as it does for
direct 'to many' relationships.

The only real difference comes with the `stars` field on the companion object:

```scala
val stars = toManyThrough[Stars, MoviesStars, Star]
```

Instead of calling `toMany` as we would do for a direct relationship, we call
`toManyThrough`. `toManyThrough` takes 3 type parameters: the target table type
(`Stars`), the join table type (`MoviesStars`) and the target type (`Star`).

Just as with direct relationships, `toManyThrough` may optionally be given a 
`toQuery` argument and a `joinCondition` argument. The above example is
essentially equivalent to: 

```scala
val stars = toManyThrough[Stars, MoviesStars, Star](
  toQuery       = TableQuery[MoviesStars] join TableQuery[Stars] on(_.starID === _.id),
  joinCondition = (m: Movies, t: (MovieStars, Stars)) => m.id === t._1.movieID)
```

With indirect relationships, the `toQuery` argument's type is a bit more 
complicated. Instead of a query that represents a set of target instances, the 
`toQuery` for an indirect relationship represents a set of 
`(JoinType, TargetType)` pairs, in this case `(MoviesStars, Stars)`. This is
achieved by joining the join table onto the target table:

```scala
toQuery = TableQuery[MoviesStars] join TableQuery[Stars] on(_.starID === _.id)
```

If you don't specify the `toQuery` argument explicitly, Entitytled will attempt
to infer a default `toQuery` at compile time by examining the foreign keys that
were defined on the join table and the target table. For Entitytled to be able
to do this, there must be exactly 1 candidate foreign key (a foreign key from
the join table to the target table, or from the target table to the join table).
If there's more than one candidate foreign key or if no candidate foreign keys
were defined, a compiler error is raised. You can always resolve these errors by
providing the `toQuery` explicitly.

The `joinCondition` argument is also slightly more complicated for indirect
relationships. Instead of simply joining the owner type to the target type, it 
has to join the owner type to this joined `(JoinType, TargetType)` pair:
 
```scala
joinCondition = (m: Movies, t: (MovieStars, Stars)) => m.id === t._1.movieID)
```

If you don't specify the `toQuery` argument explicitly, Entitytled will attempt
to infer a default `toQuery` at compile time by examining the foreign keys that
were defined on the owner table and the join table. For Entitytled to be able
to do this, there must be exactly 1 candidate foreign key (a foreign key from
the owner table to the join table, or from the join table to the owner table).
Again, if there's more than one candidate foreign key or if no candidate foreign 
keys were defined, a compiler error is raised, which can always be resolved by
providing the `joinCondition` explicitly.

To complete the example, here's the definition of the `Star` type:

```scala
case class Star(
    id: Option[Long],
    name: String)(implicit includes: Includes[Star])
  extends Entity[Star, Long]
{
  val movies = many(Star.movies)
}

object Star extends EntityCompanion[Stars, Star, Long] {
  val movies = toManyThrough[Movies, MoviesStars, Movie]
}

class Stars(tag: Tag) extends EntityTable[Star, Long](tag, "STARS") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)

  def * = (id.?, name) <> ((Star.apply _).tupled, Star.unapply)
}
```

There's nothing new going on here, it just defines the inverse relationship to
complete the full many-to-many relationship.

## Querying an entity set

The entry point for an entity query is the companion object for that entity.
Although you may also use Slick's `TableQuery` directly, the companion object
adds some additional functionality for working with entities.

As of Slick 3.0, all interaction with the database happens via a `DBIOAction`.
A `DBIOAction` represents one or more operations that are to be executed on a
database, such as a read query, inserting a new record, or deleting a record.
You then use a specific database definition to run such an actions, which will
produce a future holding the action's result:

```
db.run(someAction) // Future holding the action's result
```

If you're not familiar with Slick, I recommend you take some time to read the
following chapters from Slick's documentation before you proceed:

- [Database configuration](http://slick.typesafe.com/doc/3.0.0/database.html) to 
  learn how to setup a database definition with which you can run actions.
- [Database I/O actions](http://slick.typesafe.com/doc/3.0.0/dbio.html) to learn
  the basics about database I/O actions.
- [Queries](http://slick.typesafe.com/doc/3.0.0/queries.html) to learn the 
  basics about querying in Slick.

### Building read actions

The companion objects offers 2 methods to start building a read action:

- `all` returns an intermediate query for all entities of this type:

  ```scala
  Movie.all // Intermediate query for all movies
  ```
  
- `one` takes an entity ID as its argument and returns an intermediate query for
  the specific entity with that ID:
  
  ```scala
  Movie.one(193) // Intermediate query for the movie with ID 193
  ```
  
Both produce intermediate query results which can be modified further. This
works exactly the same as it does in Slick and you have access to all Slick's
result modifying operations (e.g. `filter`, `sortBy`, `map`, `take`, etc.). If, 
for example, you wanted to build a query for the top 10 movies with the highest 
rating, you could do this:

```
val topTenMoviesQuery = Movie.all.sortBy(_.rating.asc).take(10)
```

Just as in Slick, calling `result` on an query will return the database I/O
action:

```
val topTenMoviesAction = topTenMoviesQuery.result
```

Entitytled adds one special result modifying operation: `include`. `include` can 
be used for eager-loading relationships. Eager-loading is a way to solve the 
`n + 1` query problem:

```scala
db.run(Movie.all.result).onSuccess { _.foreach { m => 
    println(s"${m.name} was directed by:")
    println(m.director.map(_.name).getOrElse("Unknown"))
  }
}
```

If there are 1000 movies, this will execute a 1001 queries: 1 to retrieve all 
the movies and then one for each movie to retrieve its director. This is
obviously not desirable. The solution to this problem is to eager-load the
directors with `include`:

```scala
db.run(Movie.all.include(Movie.director).result).onSuccess { _.foreach { m =>
    println(s"${m.name} was directed by:")
    println(m.director.map(_.name).getOrElse("Unknown"))
  }
}
```

This modified version using `include` will execute only 2 queries: one to 
retrieve all the movies and one to retrieve all the directors related to these 
movies.

You are not limited to eager-loading a single relationship:

```scala
Movie.all.include(Movie.director, Movie.stars)
```

Nested eager-loading is also possible:

```scala
Director.all.include(Director.movies.include(Movie.stars))
```

You can eager-load an arbitrary number of relationships and nest eager-loads 
to arbitrary depth. The current implementation will execute one additional query 
for every eager-loaded relationship (both for sibling eager-loads and nested 
eager-loads).

`include` can be chained onto any query that produces an entity result:

```scala
val oldestTenMaleDirectorsWithMovies = 
  Director.all
    .filter(_.male === true)
    .sortBy(_.age.asc)
    .take(10)
    .include(Director.movies)
    .result
```

`include` cannot be chained onto queries that don't produce an entity result
(e.g. `Director.filter(_.age >== 65).size`, which will produce an integer 
result, not an entity result). 

Actions that do eager-loading cannot be streamed.

### Creating an insert action

An insert action can be created by calling the `insert` method on the companion
object, which takes the new entity as an argument:

```scala
val insertAction = Star.insert(Star(None, "Marlon Brando"))
```

This action's result will be the id of the newly inserted entity.

### Creating an update action

An update action can be created by calling the `update` method on the companion 
object, which takes the updated entity as an argument:

```scala
val updateAction = Director.update(martinScorsese.copy(age = 72))
```

Only entity instances with an ID (the ID is not `None`) can be updated, 
otherwise an exception will be thrown.

### Creating a delete action

A delete action can be created by calling the `delete` method on the companion
object, which takes the id of the entity to be deleted as an argument:

```scala
val deleteAction = Movie.delete(38)
```

## Navigating relationship values

'To one' relationships (both direct and indirect) can be used as values of type
`Option[RelatedType]`:

```scala
val director: Option[Director] = someMovie.director
```

'To many' relationships (both direct and indirect) can be used as values of type
`Seq[RelatedType]`:

```scala
val stars: Seq[Star] = someMovie.stars
```

This behaviour is achieved through implicit conversions. In reality, eager-loaded
relationship values are wrapped in `OneFetched` or `ManyFetched` for 'to one' 
and 'to many' relationships respectively; relationships that have not yet been 
loaded are represented by `OneUnfetched` or `ManyUnfetched` for 'to one' and 
'to many' relationships respectively. When implicitly converting fetched 
relationship values, the wrapped value is used; when implicitly converting
unfetched values, a query is executed to retrieve the value. For these implicit 
conversions to work, there needs to be both an implicit database definition and 
an implicit execution context in scope. 

Executing an additional query may not always be desirable. You can pattern
match for a fetched value to make the decision to execute an additional query
explicit:

```scala
someMovie.director match {
  case OneFetched(relationship, value, ownerID) =>
    println(s"The director was eager-loaded: ${value.name}!")
  case _ =>
    println("The director was not loaded yet...")
}
```

Conversely, you may want to force the execution a new query to retrieve a fresh 
value. This can be achieved by calling `fetchValue`:

```scala
someMovie.director.fetchValue
```

This will execute a new query regardless of whether the director relationship 
was eager-loaded.

## Play Framework

Entitytled does not yet provide specific integration with Play Framework.
However, using it together with [play-slick](https://github.com/playframework/play-slick)
is relatively straightforward. Add a play-slick dependency to your build for a 
matching version of Slick:

| Entitytled version | Slick version |
| ------------------ | ------------- |
| <= 0.5.x           | 2.1.x         |

(The [play-slick readme](https://github.com/playframework/play-slick/blob/master/README.md#versioning)
provides a table matching versions of the plugin to versions of Slick and 
versions of Play.)

Then define a profile as follows:

```scala
package models.meta

import entitytled.Entitytled
import play.api.db.slick.Config.{ driver => PlayDriver }

trait Profile extends Entitytled {
  val driver = PlayDriver
}

object Profile extends Profile
```

And use it like this:

```scala
import models.meta.Profile._
import models.meta.Profile.driver.simple._
```

This will make Entitytled use the Slick driver you've configured in your 
Play config file.

### Activator template

The [simple activator sample](https://typesafe.com/activator/template/play-entitytled-simple) 
gives an example of using Entitytled with Play Framework.

### Real world example

I was fortunately allowed the open-source my [master thesis research project](https://github.com/RSSchermer/pim-aid), 
for which Entitytled was originally created. I doubt it's a particularly good 
example of a Play application (feedback always welcome, please open issues!), 
but it does provides a more real world example of using Entitytled and covers 
many of the things discussed in this guide.
