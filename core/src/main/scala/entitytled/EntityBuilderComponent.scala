package entitytled

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import slick.lifted.{CanBeQueryCondition, Ordered}

import scalaz._
import Scalaz._

trait EntityBuilderComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

  trait EntityResultBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I, C[_]] {
    val query: Query[T, E, Seq]

    val includes: List[Includable[T, E]]

    def include(include: Includable[T, E]*): EntityResultBuilder[T, E, I, C]

    def result(implicit ec: ExecutionContext): DBIOAction[C[E], NoStream, Effect.Read]
  }

  /** Used to build a collection of entities along with possible includables. */
  abstract class AbstractEntityCollectionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (implicit ev: BaseColumnType[I]) extends EntityResultBuilder[T, E, I, Seq] {

    /** The base query representing the collection of entities. */
    val query: Query[T, E, Seq]

    /** The includables that should be included with the resulting entities. */
    val includes: List[Includable[T, E]] = List()

    def all: AbstractEntityCollectionBuilder[T, E, I] = this

    def one(id: I): EntityInstanceBuilder[T, E, I] =
      new EntityInstanceBuilder(query.filter(_.id === id), includes)

    /** Narrows the query to only those entities that satisfy the predicate. */
    def filter[C <: Rep[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C])
    : AbstractEntityCollectionBuilder[T, E, I] = {
      new EntityCollectionBuilder(query.filter(f), includes)
    }

    /** Narrows the query to only those entities that do not satisfy the
      * predicate. */
    def filterNot[C <: Rep[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C])
    : AbstractEntityCollectionBuilder[T, E, I] = {
      new EntityCollectionBuilder(query.filterNot(f), includes)
    }

    /** Sort this query according to a function which extracts the ordering
      * criteria from the entities. */
    def sortBy[C](f: (T) => C)(implicit arg0: (C) ⇒ Ordered)
    : AbstractEntityCollectionBuilder[T, E, I] =
      new EntityCollectionBuilder(query.sortBy(f), includes)

    /** Select the first `num` elements. */
    def take(num: Int): AbstractEntityCollectionBuilder[T, E, I] =
      new EntityCollectionBuilder(query.take(num), includes)

    /** Select all elements except the first `num` ones. */
    def drop(num: Int): AbstractEntityCollectionBuilder[T, E, I] =
      new EntityCollectionBuilder(query.drop(num), includes)

    /** Include includables on the entities in the result set. */
    def include(include: Includable[T, E]*): AbstractEntityCollectionBuilder[T, E, I] =
      new EntityCollectionBuilder(query, includes ++ include)

    def result(implicit ec: ExecutionContext): DBIOAction[Seq[E], NoStream, Effect.Read] =
      includes.foldLeft(query.result.map(_.toList))((a, i) => i.includeOn(a, query))
  }

  class EntityCollectionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](
      val query: Query[T, E, Seq],
      override val includes: List[Includable[T, E]])(implicit ev: BaseColumnType[I])
    extends AbstractEntityCollectionBuilder[T, E, I]

  class EntityInstanceBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](
      val query: Query[T, E, Seq],
      val includes: List[Includable[T, E]])(implicit ev: BaseColumnType[I])
    extends EntityResultBuilder[T, E, I, Option]
  {
    /** Include includables on the entity instance. */
    def include(include: Includable[T, E]*): EntityInstanceBuilder[T, E, I] =
      new EntityInstanceBuilder(query, includes ++ include)

    def result(implicit ec: ExecutionContext): DBIOAction[Option[E], NoStream, Effect.Read] =
      includes.foldLeft(query.result.headOption.map(x => x))((a, i) => i.includeOn(a, query))
  }
}
