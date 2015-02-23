package entitytled

trait RelationshipComponent {
  self: DriverComponent with EntityComponent with RelationshipRepComponent =>

  import driver.simple._

  /** Implement this trade to allow side-loading */
  trait SideLoadable[T <: Table[M], M] {

    /** Side-load the side-loadable on a list of instances.
      *
      * Side-load the side-loadable on the given list of instances. The given
      * query must retrieve this same list of instances.
      */
    def sideLoadOn(instances: List[M], query: Query[T, M, Seq])(implicit session: Session): List[M]

    /** Side-load the side-loadable on a single instance.
      *
      * Side-load the side-loadable on the given instance. The given
      * query must retrieve this same instance.
      */
    def sideLoadOn(instance: M, query: Query[T, M, Seq])(implicit session: Session): M
  }

  /** Represents a relationship between an owner entity and an owned relation. */
  trait Relationship[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T, Value]
    extends SideLoadable[From, E]
  {
    /** Returns a query for the owned relation for the owner entity with the
      * given id. */
    def queryFor(id: E#IdType): Query[To, T, Seq]

    /** Fetches the owned relation for the owner entity with the given id. */
    def fetchFor(id: E#IdType)(implicit session: Session): Value

    /** Fetches the owned relation for the given owner entity. */
    def fetchFor(instance: E)(implicit session: Session): Value

    /** Include side-loadables for the owned relation. */
    def include(sideLoad: SideLoadable[To, T]*): Relationship[From, To, E, T, Value]

    private[RelationshipComponent] def sideLoadQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq]
  }

  /** Base class for direct relationships (without a join-table). */
  abstract class DirectRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T, Value]
      (implicit mapping: BaseColumnType[E#IdType])
    extends Relationship[From, To, E, T, Value]
  {
    /** Query representing the complete set of owner entities for this
      * relationship.
      */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship.
      */
    val toQuery: Query[To, T, Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations.
      */
    val joinCondition: (From, To) => Column[Boolean]

    def queryFor(id: E#IdType): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2)

    private[RelationshipComponent] def sideLoadQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.innerJoin(toQuery).on(joinCondition)
  }

  /** Base class for indirect relationships (with a join-table). */
  abstract class ThroughRelationship[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity[E], T, Value]
      (implicit mapping: BaseColumnType[E#IdType])
    extends Relationship[From, To, E, T, Value]
  {
    /** Query representing the complete set of owner entities for this
      * relationship.
      */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship, joined with the join-table.
      */
    val toQuery: Query[(Through, To), _ <: (_, T), Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations joined with the join-table.
      */
    val joinCondition: (From, (Through, To)) => Column[Boolean]

    def queryFor(id: E#IdType): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2._2)

    private[RelationshipComponent] def sideLoadQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.innerJoin(toQuery).on(joinCondition).map(x => (x._1, x._2._2))
  }

  /** Implements relationship interface for 'to one' relationships. */
  trait ToOneRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T] {
    self: Relationship[From, To, E, T, Option[T]] =>

    def fetchFor(id: E#IdType)(implicit session: Session): Option[T] =
      queryFor(id).firstOption

    def fetchFor(instance: E)(implicit session: Session): Option[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => None
    }

    def include(sideLoad: SideLoadable[To, T]*): OneSideLoading[From, To, E, T] =
      new OneSideLoading(this, sideLoad)

    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildSideLoadMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](this, value))
        case _ => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](this, None))
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildSideLoadMap(query).get(instance) match {
        case Some(value) => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](this, value))
        case _ => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](this, None))
      }
    }

    private[RelationshipComponent] def buildSideLoadMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] =
      sideLoadQuery(query).list.groupBy(_._1).map(x => (x._1, x._2.map(_._2).headOption))
  }

  /** Implements relationship interface for 'to many' relationships. */
  trait ToManyRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T] {
    self: Relationship[From, To, E, T, Seq[T]] =>

    def fetchFor(id: E#IdType)(implicit session: Session): Seq[T] =
      queryFor(id).list

    def fetchFor(instance: E)(implicit session: Session): Seq[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => List()
    }

    def include(sideLoad: SideLoadable[To, T]*): ManySideLoading[From, To, E, T] =
      new ManySideLoading(this, sideLoad)

    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildSideLoadMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](this, value))
        case _ => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](this, List()))
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildSideLoadMap(query).get(instance) match {
        case Some(value) => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](this, value))
        case _ => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](this, List()))
      }
    }

    private[RelationshipComponent] def buildSideLoadMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] =
      sideLoadQuery(query).list.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  /** Represents a direct (without a join-table) 'to one' relationship. */
  class ToOne[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean])(implicit mapping: BaseColumnType[E#IdType])
    extends DirectRelationship[From, To, E, T, Option[T]]
    with ToOneRelationship[From, To, E, T]

  /** Represents a direct (without a join-table) 'to many' relationship. */
  class ToMany[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean])(implicit mapping: BaseColumnType[E#IdType])
    extends DirectRelationship[From, To, E, T, Seq[T]]
    with ToManyRelationship[From, To, E, T]

  /** Represents an indirect (with a join-table) 'to one' relationship. */
  class ToOneThrough[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity[E], T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean])(implicit mapping: BaseColumnType[E#IdType])
    extends ThroughRelationship[From, Through, To, E, T, Option[T]]
    with ToOneRelationship[From, To, E, T]

  /** Represents an indirect (with a join-table) 'to many' relationship. */
  class ToManyThrough[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity[E], T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean])(implicit mapping: BaseColumnType[E#IdType])
    extends ThroughRelationship[From, Through, To, E, T, Seq[T]]
    with ToManyRelationship[From, To, E, T]

  abstract class SideLoadingRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T, Value](
      val relationship: Relationship[From, To, E, T, Value])
    extends Relationship[From, To, E, T, Value]
  {
    def queryFor(id: E#IdType): Query[To, T, Seq] =
      relationship.queryFor(id)

    private[RelationshipComponent] def sideLoadQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      relationship.sideLoadQuery(query)
  }

  /** Wraps 'to one' relationships for side-loading one or more side-loadables. */
  class OneSideLoading[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T](
      override val relationship: Relationship[From, To, E, T, Option[T]] with ToOneRelationship[From, To, E, T],
      val sideLoads: Seq[SideLoadable[To, T]])
    extends SideLoadingRelationship[From, To, E, T, Option[T]](relationship)
    with ToOneRelationship[From, To, E, T]
  {
    override def fetchFor(id: E#IdType)(implicit session: Session): Option[T] =
      relationship.fetchFor(id) match {
        case Some(instance) =>
          val toQuery = relationship.queryFor(id)
          Some(sideLoads.foldLeft(instance)((i, s) => s.sideLoadOn(i, toQuery)))
        case _ => None
      }

    override def include(sideLoad: SideLoadable[To, T]*): OneSideLoading[From, To, E, T] =
      new OneSideLoading(relationship, sideLoads ++ sideLoad)

    override def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildSideLoadMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](relationship, value))
        case _ => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](relationship, None))
      })
    }

    override def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildSideLoadMap(query).get(instance) match {
        case Some(value) => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](relationship, value))
        case _ => instance.addInclude[T, Option[T]](Include[E, T, Option[T]](relationship, None))
      }
    }

    override private[RelationshipComponent] def buildSideLoadMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] = {
      val toQuery = sideLoadQuery(query).map(_._2)
      relationship.buildSideLoadMap(query)
        .map({
          case (e, Some(t)) =>
            (e, Some(sideLoads.foldLeft(t)((i, s) => s.sideLoadOn(i, toQuery))))
          case x@_ => x
        })
    }
  }

  /** Wraps 'to many' relationships for side-loading one or more side-loadables. */
  class ManySideLoading[From <: EntityTable[E], To <: Table[T], E <: Entity[E], T](
      override val relationship: Relationship[From, To, E, T, Seq[T]] with ToManyRelationship[From, To, E, T],
      val sideLoads: Seq[SideLoadable[To, T]])
    extends SideLoadingRelationship[From, To, E, T, Seq[T]](relationship)
    with ToManyRelationship[From, To, E, T]
  {
    override def fetchFor(id: E#IdType)(implicit session: Session): Seq[T] = {
      val toQuery = relationship.queryFor(id)
      sideLoads.foldLeft(relationship.fetchFor(id).toList)((i, s) => s.sideLoadOn(i, toQuery))
    }

    override def include(sideLoad: SideLoadable[To, T]*): ManySideLoading[From, To, E, T] =
      new ManySideLoading(relationship, sideLoads ++ sideLoad)

    override def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildSideLoadMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](relationship, value))
        case _ => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](relationship, List()))
      })
    }

    override def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildSideLoadMap(query).get(instance) match {
        case Some(value) => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](relationship, value))
        case _ => instance.addInclude[T, Seq[T]](Include[E, T, Seq[T]](relationship, List()))
      }
    }

    override private[RelationshipComponent] def buildSideLoadMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] = {
      val toQuery = sideLoadQuery(query).map(_._2)
      relationship.buildSideLoadMap(query)
        .map(x => (x._1, sideLoads.foldLeft(x._2)((i, s) => s.sideLoadOn(i.toList, toQuery))))
    }
  }
}
