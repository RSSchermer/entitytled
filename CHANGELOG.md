# Entitytled changelog

## 0.7.0

This version refactors the relationship value representation API to align
better with action based database interaction. The `getValue`, `fetchValue` and
`getOrFetchValue` operations have been replaced with `valueAction` and 
`asUnfetched`. Implicit conversions still behave the same as before. For 
details, please read the updated [Navigating relationship values](README.md#navigating-relationship-values)
section of the readme.
 
This version also introduces relationship composition:

```scala
val movies = toManyThrough[Movies, MoviesStars, Movie]
val directors = movies compose Movie.director
```

Composing a 'to many' relationship with a 'to one' relationship will result in
a new 'to many' relationship. Composing two 'to many' relationships will also
result in a new 'to many' relationship. Composing two 'to one' relationships
will result in a new 'to one' relationship.

Composed relationships behave just like normal relationships. We can add a
`directors` field on the `Star` case class for navigating this relationship:

```scala
case class Star(
    id: Option[Long],
    name: String)(implicit includes: Includes[Star])
  extends Entity[Star, Long]
{
  val movies = many(Star.movies)
  val directors = many(Star.directors)
}
```

It's also possible to further compose composed relationships with other
relationships (although at some point the queries for retrieving the composed
relationship will become so monstrous that you may want to consider creating a 
new join table to cache the relationship to improve performance).

## 0.6.0

This version upgrades to Slick 3. Slick 3 introduced some major breaking
changes and consequently, so does this. Querying has been changed to use Slick's
new database I/O actions. This means query execution operations, such as `list`
or `firstOption` have been removed. Instead, actions now need to be run using
a database definition, which results in a future containing the action's result:

```scala
db.run(Movies.all.result) // Future[Seq[Movie]]
```

This version also eliminates a lot of the boilerplate that was previously
required. It's no longer necessary to define the `query` field on entity 
companion objects, a default query is now provided:

```scala
object Director extends EntityCompanion[Directors, Director, Long] {  
  val query: Query[Directors, Director, Seq] = TableQuery[Directors]
}

// Now simplifies to:

object Director extends EntityCompanion[Directors, Director, Long]
```

Defaults are now also provided for relationship definitions, both direct and
indirect:

```scala 
val director = toOne[Directors, Director](
  toQuery       = TableQuery[Directors],
  joinCondition = (m: Movies, d: Directors) => m.directorID === d.id
)

// Now simplifies to:

val director = toOne[Directors, Director]
```

## 0.5.0

Replaced the old implementation for eager-loading, which used runtime reflection,
with a new implementation using implicit macros. Entitytled should no longer be
using any runtime reflection (at least according to `-Xlog-reflective-calls`).
 
This *should* not have broken any backwards compatibility. Overriding
`withIncludes` on your entity types is no longer necessary for improved runtime
performance and safety.
