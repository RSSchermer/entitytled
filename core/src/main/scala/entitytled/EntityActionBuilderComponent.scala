package entitytled

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.language.implicitConversions

import slick.lifted.{CanBeQueryCondition, Ordered}

import scalaz._
import Scalaz._

trait EntityActionBuilderComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._
  
  trait EntityActionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I, C[_]] {
    val query: Query[T, E, Seq]

    val includes: List[Includable[T, E]]

    def include(include: Includable[T, E]*): EntityActionBuilder[T, E, I, C]

    def result(implicit ec: ExecutionContext): DBIOAction[C[E], NoStream, Effect.Read]
  }

  /** Used to build a collection of entities along with possible includables. */
  class EntityCollectionActionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](
      val query: Query[T, E, Seq],
      val includes: List[Includable[T, E]] = List())
    extends EntityActionBuilder[T, E, I, Seq] 
  {
    
    def one(id: I)(implicit ev: BaseColumnType[I]): EntityInstanceActionBuilder[T, E, I] =
      new EntityInstanceActionBuilder(query.filter(_.id === id), includes)

    /** Narrows the query to only those entities that satisfy the predicate. */
    def filter[C <: Rep[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C])
    : EntityCollectionActionBuilder[T, E, I] = {
      new EntityCollectionActionBuilder(query.filter(f), includes)
    }

    /** Narrows the query to only those entities that do not satisfy the
      * predicate. */
    def filterNot[C <: Rep[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C])
    : EntityCollectionActionBuilder[T, E, I] = {
      new EntityCollectionActionBuilder(query.filterNot(f), includes)
    }

    /** Sort this query according to a function which extracts the ordering
      * criteria from the entities. */
    def sortBy[C](f: (T) => C)(implicit arg0: (C) ⇒ Ordered)
    : EntityCollectionActionBuilder[T, E, I] =
      new EntityCollectionActionBuilder(query.sortBy(f), includes)

    /** Select the first `num` elements. */
    def take(num: Int): EntityCollectionActionBuilder[T, E, I] =
      new EntityCollectionActionBuilder(query.take(num), includes)

    /** Select all elements except the first `num` ones. */
    def drop(num: Int): EntityCollectionActionBuilder[T, E, I] =
      new EntityCollectionActionBuilder(query.drop(num), includes)

    /** Include includables on the entities in the result set. */
    def include(include: Includable[T, E]*): EntityCollectionActionBuilder[T, E, I] =
      new EntityCollectionActionBuilder(query, includes ++ include)

    def result(implicit ec: ExecutionContext): DBIOAction[Seq[E], NoStream, Effect.Read] =
      includes.foldLeft(query.result.map(_.toList))((a, i) => i.includeOn(a, query))
  }

  class EntityInstanceActionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](
      val query: Query[T, E, Seq],
      val includes: List[Includable[T, E]])
    extends EntityActionBuilder[T, E, I, Option]
  {
    /** Include includables on the entity instance. */
    def include(include: Includable[T, E]*): EntityInstanceActionBuilder[T, E, I] =
      new EntityInstanceActionBuilder(query, includes ++ include)

    def result(implicit ec: ExecutionContext): DBIOAction[Option[E], NoStream, Effect.Read] =
      includes.foldLeft(query.result.headOption.map(x => x))((a, i) => i.includeOn(a, query))
  }
}

trait EntityActionBuilderConversionsComponent {
  self: DriverComponent
    with EntityComponent
    with EntityActionBuilderComponent
  =>

  import driver.api._

  implicit def queryToCollectionBuilder[T <: EntityTable[E, _], E <: Entity[E, _]]
  (query: Query[T, E, Seq]): EntityCollectionActionBuilder[T, E, E#IdType] =
    new EntityCollectionActionBuilder[T, E, E#IdType](query)

  implicit def actionBuilderToQuery[T <: EntityTable[E, I], E <: Entity[E, I], I, C[_]]
  (actionBuilder: EntityActionBuilder[T, E, I, C]): Query[T, E, Seq] =
    actionBuilder.query
}
