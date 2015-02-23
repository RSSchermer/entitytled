package entitytled

import scala.slick.lifted.{CanBeQueryCondition, Ordered}

trait EntityBuilderComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.simple._

  /** Used to build a collection of entities along with possible side-loadables. */
  abstract class AbstractEntityCollectionBuilder[T <: EntityTable[E], E <: Entity[E]](implicit ev: BaseColumnType[E#IdType])
  {
    /** The base query representing the collection of entities. */
    val query: Query[T, E, Seq]

    /** The side-loadables that should be included with the resulting entities. */
    val sideLoads: List[SideLoadable[T, E]] = List()

    /** Narrows the query to one specific entity with the specified ID. */
    def one(id: E#IdType) =
      new EntityInstanceBuilder[T, E](query.filter(_.id === id), sideLoads)

    /** Narrows the query to only those entities that satisfy the predicate. */
    def filter[C <: Column[_]](f: (T) => C)(implicit wt: CanBeQueryCondition[C]) = {
      new EntityCollectionBuilder[T, E](query.filter(f), sideLoads)
    }

    /** Sort this query according to a function which extracts the ordering
      * criteria from the entities. */
    def sortBy[C](f: (T) => C)(implicit arg0: (C) ⇒ Ordered) =
      new EntityCollectionBuilder[T, E](query.sortBy(f), sideLoads)

    /** Select the first `num` elements. */
    def take(num: Int) =
      new EntityCollectionBuilder[T, E](query.take(num), sideLoads)

    /** Select all elements except the first `num` ones. */
    def drop(num: Int) =
      new EntityCollectionBuilder[T, E](query.drop(num), sideLoads)

    /** Return the first entity of the result set wrapped in Some, or None if
      * the result set is empty. */
    def firstOption(implicit session: Session): Option[E] = query.firstOption match {
      case Some(instance) =>
        Some(sideLoads.foldLeft(instance)((i, s) => s.sideLoadOn(i, query)))
      case _ => None
    }

    /** Returns a list of the entities in the result set. */
    def list(implicit session: Session): Seq[E] = {
      val instances = query.list
      sideLoads.foldLeft(instances)((i, s) => s.sideLoadOn(i, query))
    }

    /** Returns the entity with the given ID wrapped in Some, or None if
      * no such entity exists. */
    def find(key: E#IdType)(implicit s: Session): Option[E] =
      one(key).get

    /** Include side-loadables on the entities in the result set. */
    def include(sideLoad: SideLoadable[T, E]*) =
      new EntityCollectionBuilder[T, E](query, sideLoads ++ sideLoad)
  }

  class EntityCollectionBuilder[T <: EntityTable[E], E <: Entity[E]](
      val query: Query[T, E, Seq],
      override val sideLoads: List[SideLoadable[T, E]])(implicit ev: BaseColumnType[E#IdType])
    extends AbstractEntityCollectionBuilder[T, E]

  /** Used to build an entity along with possible side-loadables. */
  class EntityInstanceBuilder[T <: EntityTable[E], E <: Entity[E]](
      val query: Query[T, E, Seq],
      val sideLoads: List[SideLoadable[T, E]])
  {
    /** Returns the entity wrapped in Some, or None if the query for the
      * entity does not return any rows. */
    def get(implicit session: Session): Option[E] = query.firstOption match {
      case Some(instance) =>
        Some(sideLoads.foldLeft(instance)((i, s) => s.sideLoadOn(i, query)))
      case _ => None
    }

    /** Include side-loadables on the entity. */
    def include(sideLoad: SideLoadable[T, E]*) =
      new EntityInstanceBuilder[T, E](query, sideLoads ++ sideLoad)
  }
}
