package entitytled

trait RelationshipComponent {
  self: DriverComponent with EntityComponent with RelationshipRepComponent =>

  import driver.simple._

  /** Implement this trade to allow including */
  trait Includable[T <: Table[M], M] {

    /** Include the includable on a list of instances.
      *
      * Include the includable on the given list of instances. The given
      * query must retrieve this same list of instances. */
    def includeOn(instances: List[M], query: Query[T, M, Seq])(implicit session: Session): List[M]

    /** Include the includable on a single instance.
      *
      * Include the includable on the given instance. The given
      * query must retrieve this same instance. */
    def includeOn(instance: M, query: Query[T, M, Seq])(implicit session: Session): M
  }

  /** Represents a relationship between an owner entity and an owned relation. */
  trait Relationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, Value]
    extends Includable[From, E]
  {
    /** Returns a query for the owned relation for the owner entity with the
      * given id. */
    def queryFor(id: I): Query[To, T, Seq]

    /** Fetches the owned relation for the owner entity with the given id. */
    def fetchFor(id: I)(implicit session: Session): Value

    /** Fetches the owned relation for the given owner entity. */
    def fetchFor(instance: E)(implicit session: Session): Value

    /** Include includables for the owned relation. */
    def include(includables: Includable[To, T]*): Relationship[From, To, E, I, T, Value]

    private[RelationshipComponent] def includeQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq]
  }

  /** Base class for direct relationships (without a join-table). */
  abstract class DirectRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, Value]
      (implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, Value]
  {
    /** Query representing the complete set of owner entities for this
      * relationship. */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship. */
    val toQuery: Query[To, T, Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations. */
    val joinCondition: (From, To) => Column[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2)

    private[RelationshipComponent] def includeQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.innerJoin(toQuery).on(joinCondition)
  }

  /** Base class for indirect relationships (with a join-table). */
  abstract class ThroughRelationship[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T, Value]
      (implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, Value]
  {
    /** Query representing the complete set of owner entities for this
      * relationship. */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship, joined with the join-table. */
    val toQuery: Query[(Through, To), _ <: (_, T), Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations joined with the join-table. */
    val joinCondition: (From, (Through, To)) => Column[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2._2)

    private[RelationshipComponent] def includeQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.innerJoin(toQuery).on(joinCondition).map(x => (x._1, x._2._2))
  }

  /** Implements relationship interface for 'to one' relationships. */
  trait ToOneRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T] {
    self: Relationship[From, To, E, I, T, Option[T]] =>

    def fetchFor(id: I)(implicit session: Session): Option[T] =
      queryFor(id).firstOption

    def fetchFor(instance: E)(implicit session: Session): Option[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => None
    }

    def include(includables: Includable[To, T]*): OneIncluding[From, To, E, I, T] =
      new OneIncluding(this, includables)

    def includeOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildIncludeMap(query)

      instances.map(i => i.setInclude(this, map.getOrElse(i, None)))
    }

    def includeOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E =
      instance.setInclude(this, buildIncludeMap(query).getOrElse(instance, None))

    private[RelationshipComponent] def buildIncludeMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] =
      includeQuery(query).list.groupBy(_._1).map(x => (x._1, x._2.map(_._2).headOption))
  }

  /** Implements relationship interface for 'to many' relationships. */
  trait ToManyRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T] {
    self: Relationship[From, To, E, I, T, Seq[T]] =>

    def fetchFor(id: I)(implicit session: Session): Seq[T] =
      queryFor(id).list

    def fetchFor(instance: E)(implicit session: Session): Seq[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => List()
    }

    def include(includables: Includable[To, T]*): ManyIncluding[From, To, E, I, T] =
      new ManyIncluding(this, includables)

    def includeOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildIncludeMap(query)

      instances.map(i => i.setInclude(this, map.getOrElse(i, List())))
    }

    def includeOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E =
      instance.setInclude(this, buildIncludeMap(query).getOrElse(instance, List()))

    private[RelationshipComponent] def buildIncludeMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] =
      includeQuery(query).list.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  /** Represents a direct (without a join-table) 'to one' relationship. */
  class ToOne[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean])(implicit mapping: BaseColumnType[I])
    extends DirectRelationship[From, To, E, I, T, Option[T]]
    with ToOneRelationship[From, To, E, I, T]

  /** Represents a direct (without a join-table) 'to many' relationship. */
  class ToMany[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean])(implicit mapping: BaseColumnType[I])
    extends DirectRelationship[From, To, E, I, T, Seq[T]]
    with ToManyRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to one' relationship. */
  class ToOneThrough[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean])(implicit mapping: BaseColumnType[I])
    extends ThroughRelationship[From, Through, To, E, I, T, Option[T]]
    with ToOneRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to many' relationship. */
  class ToManyThrough[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean])(implicit mapping: BaseColumnType[I])
    extends ThroughRelationship[From, Through, To, E, I, T, Seq[T]]
    with ToManyRelationship[From, To, E, I, T]

  abstract class IncludingRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, Value](
      val relationship: Relationship[From, To, E, I, T, Value])
    extends Relationship[From, To, E, I, T, Value]
  {
    def queryFor(id: I): Query[To, T, Seq] =
      relationship.queryFor(id)

    private[RelationshipComponent] def includeQuery(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      relationship.includeQuery(query)
  }

  /** Wraps 'to one' relationships for including one or more includables. */
  class OneIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Option[T]] with ToOneRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, T]])
    extends IncludingRelationship[From, To, E, I, T, Option[T]](relationship)
    with ToOneRelationship[From, To, E, I, T]
  {
    override def fetchFor(id: I)(implicit session: Session): Option[T] =
      relationship.fetchFor(id) match {
        case Some(instance) =>
          val toQuery = relationship.queryFor(id)
          Some(includes.foldLeft(instance)((i, s) => s.includeOn(i, toQuery)))
        case _ => None
      }

    override def include(includables: Includable[To, T]*): OneIncluding[From, To, E, I, T] =
      new OneIncluding(relationship, includes ++ includables)

    override def includeOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildIncludeMap(query)

      instances.map(i => i.setInclude(relationship, map.getOrElse(i, None)))
    }

    override def includeOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E =
      instance.setInclude(relationship, buildIncludeMap(query).getOrElse(instance, None))

    override private[RelationshipComponent] def buildIncludeMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] = {
      val toQuery = includeQuery(query).map(_._2)
      relationship.buildIncludeMap(query)
        .map({
          case (e, Some(t)) =>
            (e, Some(includes.foldLeft(t)((i, s) => s.includeOn(i, toQuery))))
          case x@_ => x
        })
    }
  }

  /** Wraps 'to many' relationships for including one or more includables. */
  class ManyIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Seq[T]] with ToManyRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, T]])
    extends IncludingRelationship[From, To, E, I, T, Seq[T]](relationship)
    with ToManyRelationship[From, To, E, I, T]
  {
    override def fetchFor(id: I)(implicit session: Session): Seq[T] = {
      val toQuery = relationship.queryFor(id)
      
      includes.foldLeft(relationship.fetchFor(id).toList)((i, s) => s.includeOn(i, toQuery))
    }

    override def include(includables: Includable[To, T]*): ManyIncluding[From, To, E, I, T] =
      new ManyIncluding(relationship, includes ++ includables)

    override def includeOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildIncludeMap(query)

      instances.map(i => i.setInclude(relationship, map.getOrElse(i, List())))
    }

    override def includeOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E =
      instance.setInclude(relationship, buildIncludeMap(query).getOrElse(instance, List()))

    override private[RelationshipComponent] def buildIncludeMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] = {
      val toQuery = includeQuery(query).map(_._2)
      relationship.buildIncludeMap(query)
        .map(x => (x._1, includes.foldLeft(x._2)((i, s) => s.includeOn(i.toList, toQuery))))
    }
  }
}
