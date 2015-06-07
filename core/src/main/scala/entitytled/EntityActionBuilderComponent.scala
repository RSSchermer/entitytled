package entitytled

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.language.implicitConversions

import slick.lifted.{CanBeQueryCondition, Ordered}

import scalaz._
import Scalaz._

/** Component grouping some declarations concerning entity action builders.
  *
  * Should be mixed in along with a [[DriverComponent]], an [[EntityComponent]]
  * and a [[RelationshipComponent]].
  */
trait EntityActionBuilderComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

  /** Base trait for entity action builders, which allow eager-loading
    * includables onto entity queries.
    *
    * @tparam T The entity's table type.
    * @tparam E The entity's type.
    * @tparam C The result container type (e.g. [[scala.Option]], [[scala.Seq]]).
    */
  trait EntityActionBuilder[T <: EntityTable[E, _], E <: Entity[E, _], C[_]] {

    /** The underlying entity query. */
    val query: Query[T, E, Seq]

    /** List of includables to be included (eager-loaded) onto the resulting
      * entities
      */
    val includes: List[Includable[T, E]]

    /** Returns a new entity action builder with the given includables added
      * to the previously included includables.
      *
      * @param include One or more includables to add to the resulting entities.
      */
    def include(include: Includable[T, E]*): EntityActionBuilder[T, E, C]
  }

  /** Used to build an action which will result in a collection of entities
    * onto which the included includables will be eager-loaded.
    */
  class EntityCollectionActionBuilder[T <: EntityTable[E, _], E <: Entity[E, _]](
      val query: Query[T, E, Seq],
      val includes: List[Includable[T, E]] = List())
    extends EntityActionBuilder[T, E, Seq]
  {

    /** Narrows the query to only those entities that satisfy the predicate.
      *
      * @see [[slick.lifted.Query.filter()]]
      */
    def filter[C <: Rep[_] : CanBeQueryCondition](f: (T) => C): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query.filter(f), includes)

    /** Narrows the query to only those entities that do not satisfy the
      * predicate.
      *
      * @see [[slick.lifted.Query.filterNot()]]
      */
    def filterNot[C <: Rep[_] : CanBeQueryCondition](f: (T) => C): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query.filterNot(f), includes)

    /** Sort this query according to a function which extracts the ordering
      * criteria from the entities.
      *
      * @see [[slick.lifted.Query.sortBy()]]
      */
    def sortBy[C](f: (T) => C)(implicit arg0: (C) => Ordered): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query.sortBy(f), includes)

    /** Select the first `num` elements.
      *
      * @see [[slick.lifted.Query.take()]]
      */
    def take(num: Int): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query.take(num), includes)

    /** Select all elements except the first `num` ones.
      *
      * @see [[slick.lifted.Query.drop()]]
      */
    def drop(num: Int): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query.drop(num), includes)

    def include(include: Includable[T, E]*): EntityCollectionActionBuilder[T, E] =
      new EntityCollectionActionBuilder(query, includes ++ include)
  }

  /** Adds a `result` method to an entity collection action builder.
    *
    * The result method was initially defined on the entity collection action
    * builder. However, this mean that when calling `result` on plain Slick
    * queries, the conversion to an entity collection action builder took
    * priority over the conversion Slick defines.
    *
    * @param builder The entity collection action builder from which to build
    *                the result action.
    *
    * @tparam T The entity's table type.
    * @tparam E The entity's type.
    */
  implicit class EntityCollectionResultInvoker[T <: EntityTable[E, _], E <: Entity[E, _]]
  (builder: EntityCollectionActionBuilder[T, E]) {

    /** Turns the entity collection action builder into an entity result action
      * onto which the included includables are eager-loaded.
      */
    def result(implicit ec: ExecutionContext): DBIOAction[Seq[E], NoStream, Effect.Read] =
      builder.includes.foldLeft(builder.query.result.map(_.toList))((a, i) => i.includeOn(a, builder.query))
  }

  /** Used to build an action which will result in a single entity instance
    * onto which the included includables will be eager-loaded.
    */
  class EntityInstanceActionBuilder[T <: EntityTable[E, _], E <: Entity[E, _]](
      val query: Query[T, E, Seq],
      val includes: List[Includable[T, E]] = List())
    extends EntityActionBuilder[T, E, Option]
  {
    def include(include: Includable[T, E]*): EntityInstanceActionBuilder[T, E] =
      new EntityInstanceActionBuilder(query, includes ++ include)

    /** Turns the entity instance action builder into an entity result action
      * onto which the included includables are eager-loaded.
      */
    def result(implicit ec: ExecutionContext): DBIOAction[Option[E], NoStream, Effect.Read] =
      includes.foldLeft(query.result.headOption.map(x => x))((a, i) => i.includeOn(a, query))
  }
}

/** Component containing implicit conversions from an entity action builder to
  * a Slick query and from a Slick query to an entity action builder.
  *
  * Should be mixed in along with a [[DriverComponent]], an [[EntityComponent]]
  * and an [[EntityActionBuilderComponent]].
  */
trait EntityActionBuilderConversionsComponent {
  self: DriverComponent
    with EntityComponent
    with EntityActionBuilderComponent
  =>

  import driver.api._

  implicit def queryToCollectionBuilder[T <: EntityTable[E, _], E <: Entity[E, _]]
  (query: Query[T, E, Seq]): EntityCollectionActionBuilder[T, E] =
    new EntityCollectionActionBuilder[T, E](query)

  implicit def actionBuilderToQuery[T <: EntityTable[E, _], E <: Entity[E, _], C[_]]
  (actionBuilder: EntityActionBuilder[T, E, C]): Query[T, E, Seq] =
    actionBuilder.query
}
