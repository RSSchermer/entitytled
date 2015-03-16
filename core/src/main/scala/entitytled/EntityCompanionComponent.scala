package entitytled

trait EntityCompanionComponent {
  self: DriverComponent
    with EntityComponent
    with RelationshipComponent
    with RelationshipRepComponent
    with EntityRepositoryComponent
  =>

  import driver.simple._

  /** Trait for entity companion objects. */
  abstract class EntityCompanion[T <: EntityTable[E], E <: Entity[E]](implicit ev: BaseColumnType[E#IdType])
    extends EntityRepository[T, E]
  {
    implicit val defaultIncludes: Includes[E] = Map()

    val query: Query[T, E, Seq]

    /** Creates a new direct (without a join-table) 'to one' relationship */
    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean]
    ): ToOne[T, To, E, M] =
      new ToOne[T, To, E, M](query, toQuery, joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship */
    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Column[Boolean]
    ): ToMany[T, To, E, M] =
      new ToMany[T, To, E, M](query, toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Column[Boolean]
    ): ToOneThrough[T, Through, To, E, M] =
      new ToOneThrough[T, Through, To, E, M](query, toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Column[Boolean]
    ): ToManyThrough[T, Through, To, E, M] =
      new ToManyThrough[T, Through, To, E, M](query, toQuery, joinCondition)
  }
}
