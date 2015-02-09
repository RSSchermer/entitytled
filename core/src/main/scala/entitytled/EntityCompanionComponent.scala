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

  trait EntityCompanion[T <: EntityTable[E], E <: Entity] {
    val query: Query[T, E, Seq]

    val lenser = Lenser[E]

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean],
      propertyLens: Lens[E, One[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToOne[T, To, E, M] =
      new ToOne[T, To, E, M](query, toQuery, joinCondition, propertyLens)

    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean],
      propertyLens: Lens[E, Many[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToMany[T, To, E, M] =
      new ToMany[T, To, E, M](query, toQuery, joinCondition, propertyLens)

    protected def toManyThrough[To <: Table[M], Through <: Table[J], M, J](
      toQuery: Query[(Through, To), (J, M), Seq],
      joinCondition: (T, (Through, To)) => Column[Boolean],
      propertyLens: Lens[E, Many[E, M]]
    )(implicit mapping: BaseColumnType[E#IdType]): ToManyThrough[T, Through, To, E, J, M] =
      new ToManyThrough[T, Through, To, E, J, M](query, toQuery, joinCondition, propertyLens)
  }
}
