package entitytled

import scala.slick.lifted.{CanBeQueryCondition, Ordered}

trait EntityBuilderComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.simple._

  /** Used to build a collection of entities along with possible includables. */
  abstract class AbstractEntityCollectionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](implicit ev: BaseColumnType[I])
  {
    /** The base query representing the collection of entities. */
    val query: Query[T, E, Seq]

    /** The includables that should be included with the resulting entities. */
    val includes: List[Includable[T, E]] = List()

    /** Narrows the query to only those entities that satisfy the predicate. */
    def filter[C <: Column[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C]) = {
      new EntityCollectionBuilder[T, E, I](query.filter(f), includes)
    }

    /** Narrows the query to only those entities that do not satisfy the
      * predicate. */
    def filterNot[C <: Column[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C]) = {
      new EntityCollectionBuilder[T, E, I](query.filterNot(f), includes)
    }

    /** Sort this query according to a function which extracts the ordering
      * criteria from the entities. */
    def sortBy[C](f: (T) => C)(implicit arg0: (C) ⇒ Ordered) =
      new EntityCollectionBuilder[T, E, I](query.sortBy(f), includes)

    /** Select the first `num` elements. */
    def take(num: Int) =
      new EntityCollectionBuilder[T, E, I](query.take(num), includes)

    /** Select all elements except the first `num` ones. */
    def drop(num: Int) =
      new EntityCollectionBuilder[T, E, I](query.drop(num), includes)

    /** Return the first entity of the result set wrapped in Some, or None if
      * the result set is empty. */
    def firstOption()(implicit session: Session): Option[E] = query.firstOption match {
      case Some(instance) =>
        Some(includes.foldLeft(instance)((i, s) => s.includeOn(i, query)))
      case _ => None
    }

    /** Returns a list of the entities in the result set. */
    def list()(implicit session: Session): Seq[E] = {
      val instances = query.list
      includes.foldLeft(instances)((i, s) => s.includeOn(i, query))
    }

    /** Returns the entity with the given ID wrapped in Some, or None if
      * no such entity exists. */
    def find(id: I)(implicit session: Session): Option[E] =
      query.filter(_.id === id).firstOption match {
        case Some(instance) =>
          Some(includes.foldLeft(instance)((i, s) => s.includeOn(i, query)))
        case _ => None
      }

    /** Include includables on the entities in the result set. */
    def include(include: Includable[T, E]*) =
      new EntityCollectionBuilder[T, E, I](query, includes ++ include)
  }

  class EntityCollectionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I](
      val query: Query[T, E, Seq],
      override val includes: List[Includable[T, E]])(implicit ev: BaseColumnType[I])
    extends AbstractEntityCollectionBuilder[T, E, I]
}
