package entitytled

import monocle.Lens
import monocle.macros.Lenser

trait EntityCompanionComponent {
  self: DriverComponent
    with TableComponent
    with RelationshipComponent
    with RelationshipRepComponent
  =>

  import driver.simple._

  /** Trait for entity companion objects. */
  trait EntityCompanion[T <: EntityTable[E], E <: Entity] {
    val query: Query[T, E, Seq]

    val lenser = Lenser[E]

    /** Creates a new direct (without a join-table) 'to one' relationship */
    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean],
      propertyLens: Lens[E, One[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToOne[T, To, E, M] =
      new ToOne[T, To, E, M](query, toQuery, joinCondition, propertyLens)

    /** Creates a new direct (without a join-table) 'to many' relationship */
    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean],
      propertyLens: Lens[E, Many[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToMany[T, To, E, M] =
      new ToMany[T, To, E, M](query, toQuery, joinCondition, propertyLens)

    /** Creates a new indirect (with a join-table) 'to one' relationship */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Column[Boolean],
      propertyLens: Lens[E, One[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToOneThrough[T, Through, To, E, M] =
      new ToOneThrough[T, Through, To, E, M](query, toQuery, joinCondition, propertyLens)

    /** Creates a new indirect (with a join-table) 'to many' relationship */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Column[Boolean],
      propertyLens: Lens[E, Many[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToManyThrough[T, Through, To, E, M] =
      new ToManyThrough[T, Through, To, E, M](query, toQuery, joinCondition, propertyLens)
  }
}
