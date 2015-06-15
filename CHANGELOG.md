# Entitytled changelog

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
