package entitytled

import scala.language.higherKinds

import scala.concurrent.ExecutionContext

trait RelationshipComponent {
  self: DriverComponent with EntityComponent with RelationshipRepComponent =>

  import driver.api._

  /** Implement this trait to allow including */
  trait Includable[Owner <: Table[O], To <: Table[T], O, T] {

    /** Include the includable on a list of instances.
      *
      * Include the includable on the given list of instances. The given
      * query must retrieve this same list of instances. */
    def includeOnOption(
      action: DBIOAction[Option[O], NoStream, Effect.Read],
      query: Query[Owner, O, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Option[O], NoStream, Effect.Read]

    def includeOnSeq(
      action: DBIOAction[Seq[O], NoStream, Effect.Read],
      query: Query[Owner, O, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Seq[O], NoStream, Effect.Read]

    def includeOnMap[K](
      action: DBIOAction[Map[K, O], NoStream, Effect.Read],
      query: Query[Owner, O, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Map[K, O], NoStream, Effect.Read]
  }

  /** Represents a relationship between an owner entity and an owned relation. */
  trait Relationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, +C[_]]
    extends Includable[From, To, E, T]
  {
    /** Returns a query for the owned relation for the owner entity with the
      * given id. */
    def queryFor(id: I): Query[To, T, Seq]
    
    def actionFor(id: I)(implicit ec: ExecutionContext): DBIOAction[C[T], NoStream, Effect.Read]

    val emptyValue: C[T]

    val inclusionKey: Relationship[From, To, E, I, T, C] = this

    /** Include includables for the owned relation. */
    def include(includables: Includable[To, _ <: Table[_], T, _]*): Relationship[From, To, E, I, T, C]

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq]

    def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, C[T]], NoStream, Effect.Read]

    def includeOnOption(
      action: DBIOAction[Option[E], NoStream, Effect.Read],
      query: Query[From, E, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Option[E], NoStream, Effect.Read] =
      action.zip(inclusionActionFor(query)).map { value =>
        value._1.map(e => e.setInclude(inclusionKey, value._2.getOrElse(e, emptyValue)))
      }

    def includeOnSeq(
      action: DBIOAction[Seq[E], NoStream, Effect.Read],
      query: Query[From, E, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Seq[E], NoStream, Effect.Read] =
      action.zip(inclusionActionFor(query)).map { value =>
        value._1.map(e => e.setInclude(inclusionKey, value._2.getOrElse(e, emptyValue)))
      }

    def includeOnMap[K](
      action: DBIOAction[Map[K, E], NoStream, Effect.Read],
      query: Query[From, E, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[Map[K, E], NoStream, Effect.Read] =
      action.zip(inclusionActionFor(query)).map { value =>
        value._1.mapValues(e => e.setInclude(inclusionKey, value._2.getOrElse(e, emptyValue)))
      }
  }

  /** Base class for direct relationships (without a join-table). */
  abstract class DirectRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, +C[_]]
      (implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, C]
  {
    /** Query representing the complete set of owner entities for this
      * relationship. */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship. */
    val toQuery: Query[To, T, Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations. */
    val joinCondition: (From, To) => Rep[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).join(toQuery).on(joinCondition).map(_._2)

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.join(toQuery).on(joinCondition)
  }

  /** Base class for indirect relationships (with a join-table). */
  abstract class ThroughRelationship[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T, +C[_]]
      (implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, C]
  {
    /** Query representing the complete set of owner entities for this
      * relationship. */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of owned relations for this
      * relationship, joined with the join-table. */
    val toQuery: Query[(Through, To), _ <: (_, T), Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations joined with the join-table. */
    val joinCondition: (From, (Through, To)) => Rep[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).join(toQuery).on(joinCondition).map(_._2._2)

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      query.join(toQuery).on(joinCondition).map(x => (x._1, x._2._2))
  }

  /** Implements relationship interface for 'to one' relationships. */
  trait ToOneRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T] {
    self: Relationship[From, To, E, I, T, Option] =>

    val emptyValue: Option[T] = None

    def actionFor(id: I)(implicit ec: ExecutionContext): DBIOAction[Option[T], NoStream, Effect.Read] =
      queryFor(id).result.headOption
    
    def include(includables: Includable[To, _ <: Table[_], T, _]*): OneIncluding[From, To, E, I, T] =
      new OneIncluding(this, includables)

    def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Option[T]], NoStream, Effect.Read] =
      inclusionQueryFor(query).result.map(_.groupBy(_._1).map(x => (x._1, x._2.map(_._2).headOption)))
  }

  /** Implements relationship interface for 'to many' relationships. */
  trait ToManyRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T] {
    self: Relationship[From, To, E, I, T, Seq] =>

    val emptyValue: Seq[T] = List()

    def actionFor(id: I)(implicit ec: ExecutionContext): DBIOAction[Seq[T], NoStream, Effect.Read] =
      queryFor(id).result
    
    def include(includables: Includable[To, _ <: Table[_], T, _]*): ManyIncluding[From, To, E, I, T] =
      new ManyIncluding(this, includables)

    def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Seq[T]], NoStream, Effect.Read] =
      inclusionQueryFor(query).result.map(_.groupBy(_._1).map(x => (x._1, x._2.map(_._2))))
  }

  /** Represents a direct (without a join-table) 'to one' relationship. */
  class ToOne[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Rep[Boolean])(implicit mapping: BaseColumnType[I])
    extends DirectRelationship[From, To, E, I, T, Option]
    with ToOneRelationship[From, To, E, I, T]

  /** Represents a direct (without a join-table) 'to many' relationship. */
  class ToMany[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Rep[Boolean])(implicit mapping: BaseColumnType[I])
    extends DirectRelationship[From, To, E, I, T, Seq]
    with ToManyRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to one' relationship. */
  class ToOneThrough[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Rep[Boolean])(implicit mapping: BaseColumnType[I])
    extends ThroughRelationship[From, Through, To, E, I, T, Option]
    with ToOneRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to many' relationship. */
  class ToManyThrough[From <: EntityTable[E, I], Through <: Table[_], To <: Table[T], E <: Entity[E, I], I, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Rep[Boolean])(implicit mapping: BaseColumnType[I])
    extends ThroughRelationship[From, Through, To, E, I, T, Seq]
    with ToManyRelationship[From, To, E, I, T]

  abstract class IncludingRelationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, +C[_]](
      val relationship: Relationship[From, To, E, I, T, C])
    extends Relationship[From, To, E, I, T, C]
  {
    val includes: Seq[Includable[To, _ <: Table[_], T, _]]

    override val inclusionKey: Relationship[From, To, E, I, T, C] = relationship.inclusionKey

    def queryFor(id: I): Query[To, T, Seq] =
      relationship.queryFor(id)

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      relationship.inclusionQueryFor(query)
  }

  /** Wraps 'to one' relationships for including one or more includables. */
  class OneIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Option] with ToOneRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, _ <: Table[_], T, _]])
    extends IncludingRelationship[From, To, E, I, T, Option](relationship)
    with ToOneRelationship[From, To, E, I, T]
  {
    override def actionFor(id: I)(implicit ec: ExecutionContext)
    : DBIOAction[Option[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action)((a, i) => i.includeOnOption(a, query))
    }

    override def include(includables: Includable[To, _ <: Table[_], T, _]*): OneIncluding[From, To, E, I, T] =
      new OneIncluding(relationship, includes ++ includables)

    override def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Option[T]], NoStream, Effect.Read] = {
      val inclusionQuery = inclusionQueryFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result)((a, i) => i.includeOnMap(a, inclusionQuery.map(_._2)))

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.headOption)))
    }
  }

  /** Wraps 'to many' relationships for including one or more includables. */
  class ManyIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Seq] with ToManyRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, _ <: Table[_], T, _]])
    extends IncludingRelationship[From, To, E, I, T, Seq](relationship)
    with ToManyRelationship[From, To, E, I, T]
  {
    override def actionFor(id: I)(implicit ec: ExecutionContext)
    : DBIOAction[Seq[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action.map(x => x))((a, i) => i.includeOnSeq(a, query))
    }

    override def include(includables: Includable[To, _ <: Table[_], T, _]*): ManyIncluding[From, To, E, I, T] =
      new ManyIncluding(relationship, includes ++ includables)

    override def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Seq[T]], NoStream, Effect.Read] = {
      val inclusionQuery = inclusionQueryFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result)((a, i) => i.includeOnMap(a, inclusionQuery.map(_._2)))

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.toSeq)))
    }
  }
}
