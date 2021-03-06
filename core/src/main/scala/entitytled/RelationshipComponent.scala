package entitytled

import scala.language.higherKinds
import scala.language.experimental.macros
import scala.reflect.macros._

import scala.concurrent.ExecutionContext

import slick.lifted.{ AbstractTable, ForeignKeyQuery }

import scalaz._
import Scalaz._

/** Component grouping declarations regarding relationships.
  *
  * Needs to be mixed in along with a [[DriverComponent]] and an
  * [[EntityComponent]].
  */
trait RelationshipComponent {
  self: DriverComponent with EntityComponent =>

  import driver.api._

  /** Trait to allow including (eager-loading) related values on a query result.
    *
    * @tparam Owner The type of the table that owns the related values.
    * @tparam O     The type of the row that owns the related values.
    */
  trait Includable[Owner <: Table[O], O] {

    /** Includes this includable on a database action.
      *
      * @param action The action the includable is to be included on.
      * @param query  The query that produced the database action the includable
      *               is to be included on.
      * @param ec     The execution context in which the action is to be
      *               modified.
      * @tparam F The type of the functor that contians the owner row
      *           instance(s).
      * @return The database action, modified to include this includable on the
      *         resulting owner instances.
      */
    def includeOn[F[_]: Functor](
        action: DBIOAction[F[O], NoStream, Effect.Read],
        query: Query[Owner, O, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[F[O], NoStream, Effect.Read]
  }

  /** Represents a relationship between an owner entity and an owned relation.
    *
    * @tparam From The owner entity's table type.
    * @tparam To   The target relation's table type.
    * @tparam E    The owner entity's type.
    * @tparam I    The owner entity's ID type.
    * @tparam T    The target relation's type.
    * @tparam C    The type of the functor containing target relation values
    *              that are retrieved from the database.
    */
  trait Relationship[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T,
      +C[_]
  ] extends Includable[From, E] {

    /** Returns a query for the owned relations belonging the owner entity with
      * the given ID.
      *
      * @param id The ID of the owner entity to build the query for.
      */
    def queryFor(id: I): Query[To, T, Seq]

    /** Returns a query for all the owned relations belonging all the owner
      * entities with retrieved by the given query.
      *
      * @param query The query for owner entities for which to build a query for
      *              the owned relations.
      */
    def queryFor(query: Query[From, E, Seq]): Query[To, T, Seq]

    /** Returns a database action for retrieving the owned relations belong to
      * the owner entity with the given ID.
      *
      * @param id The ID of the owner entity to build the action for.
      * @param ec The execution context in which the action is the be build.
      */
    def actionFor(
        id: I
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[C[T], NoStream, Effect.Read]

    /** Value to be used when the owner entity does not own any target
      * relations.
      */
    val emptyValue: C[T]

    /** Modifies actions build for this relationship to include (eager-load) the
      * given includables on the owned relations.
      *
      * @param includables The includables to be included on database actions.
      */
    def include(
        includables: Includable[To, T]*
    ): Relationship[From, To, E, I, T, C]

    /** Includes this relationship on a database action.
      *
      * @param action The entity action the relationship is to be included on.
      * @param query  The query that produced the database action the
      *               relationship is to be included on.
      * @tparam F The type of the functor that contains the owner entity
      *           instance(s).
      * @return The database action, modified to include this relationship on
      *         the resulting owner instance(s).
      */
    def includeOn[F[_] : Functor](
        action: DBIOAction[F[E], NoStream, Effect.Read],
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[F[E], NoStream, Effect.Read] =
      action.zip(inclusionActionFor(query)).map { case (owners, valueMap) =>
        owners.map { e =>
          e.setInclude(inclusionKey, valueMap.getOrElse(e, emptyValue))
        }
      }

    /** Returns a query for the inner product of the given owner entity query
      * with the respective owned relations.
      *
      * @param query Query for owner entities on which the set of owned
      *              relations is to be joined.
      */
    def innerJoinFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq]

    /** Expands a join query from an origin relation to the owner relation, into
      * a join query from that origin relation to the target relation.
      *
      * @param query A join query from the origin relation to the owner
      *              relation.
      *
      * @tparam W The origin relation's table type.
      * @tparam O The origin relation's row type.
      */
    def expandJoin[W <: Table[O], O](
        query: Query[(W, From), (O, E), Seq]
    ): Query[(W, To), (O, T), Seq]

    /** Returns a database action that will result in a map of owner entities
      * to their owned values for this relationship.
      *
      * The resulting map only includes owner entities for which the
      * relationship is not empty.
      *
      * @param query The query for the relevant owner entities.
      * @param ec    The execution context this action is to be build in.
      */
    def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, C[T]], NoStream, Effect.Read]

    val inclusionKey: Relationship[From, To, E, I, T, C] = this
  }

  /** Base class for direct relationships (without a join-table). */
  abstract class DirectRelationship[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T,
      +C[_]
  ](implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, C]
  {
    /** Query representing the complete set of possible owner entities for this
      * relationship.
      */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of possible owned relations for this
      * relationship.
      */
    val toQuery: Query[To, T, Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations.
      */
    val joinCondition: (From, To) => Rep[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).join(toQuery).on(joinCondition).map(_._2)

    def queryFor(query: Query[From, E, Seq]): Query[To, T, Seq] =
      query.join(toQuery).on(joinCondition).map(_._2)

    def innerJoinFor(
        query: Query[From, E, Seq]
    ): Query[(From, To), (E, T), Seq] =
      query.join(toQuery).on(joinCondition)

    def expandJoin[W <: Table[O], O](
        query: Query[(W, From), (O, E), Seq]
    ): Query[(W, To), (O, T), Seq] =
      query.join(toQuery)
        .on((x: (W, From), t: To) => joinCondition.apply(x._2, t))
        .map(x => (x._1._1, x._2))
  }

  /** Base class for indirect relationships (with a join-table).
    *
    * @tparam Through The join-table's type.
    */
  abstract class IndirectRelationship[
      From <: EntityTable[E, I],
      Through <: Table[_],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T,
      +C[_]
  ](implicit mapping: BaseColumnType[I])
    extends Relationship[From, To, E, I, T, C]
  {
    /** Query representing the complete set of possible owner entities for this
      * relationship.
      */
    val fromQuery: Query[From, E, Seq]

    /** Query representing the complete set of possible owned relations for this
      * relationship, joined onto the join-table.
      */
    val toQuery: Query[(Through, To), _ <: (_, T), Seq]

    /** Condition for joining a query for owner entities with a query for
      * owned relations joined with the join-table.
      */
    val joinCondition: (From, (Through, To)) => Rep[Boolean]

    def queryFor(id: I): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).join(toQuery).on(joinCondition).map(_._2._2)

    def queryFor(query: Query[From, E, Seq]): Query[To, T, Seq] =
      query.join(toQuery).on(joinCondition).map(_._2._2)

    def innerJoinFor(
        query: Query[From, E, Seq]
    ): Query[(From, To), (E, T), Seq] =
      query.join(toQuery).on(joinCondition).map(x => (x._1, x._2._2))

    def expandJoin[W <: Table[O], O](
        query: Query[(W, From), (O, E), Seq]
    ): Query[(W, To), (O, T), Seq] =
      query.join(toQuery)
        .on((x: (W, From), t: (Through, To)) => joinCondition.apply(x._2, t))
        .map(x => (x._1._1, x._2._2))
  }

  /** Provides a partial implementation of [[Relationship]] for 'to one'
    * relationships.
    *
    * @tparam From The owner entity's table type.
    * @tparam To   The target relation's table type.
    * @tparam E    The owner entity's type.
    * @tparam I    The owner entity's ID type.
    * @tparam T    The target relation's type.
    */
  trait ToOneRelationship[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ] {
    self: Relationship[From, To, E, I, T, Option] =>

    val emptyValue: Option[T] = None

    def actionFor(
        id: I
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Option[T], NoStream, Effect.Read] =
      queryFor(id).result.headOption
    
    def include(
        includables: Includable[To, T]*
    ): ToOneWithIncludes[From, To, E, I, T] =
      new ToOneWithIncludes(this, includables)

    def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, Option[T]], NoStream, Effect.Read] = {
      val res = innerJoinFor(query).result
      res.map(_.groupBy(_._1).map(x => (x._1, x._2.map(_._2).headOption)))
    }
  }

  /** Provides a partial implementation of [[Relationship]] for 'to many'
    * relationships.
    *
    * @tparam From The owner entity's table type.
    * @tparam To   The target relation's table type.
    * @tparam E    The owner entity's type.
    * @tparam I    The owner entity's ID type.
    * @tparam T    The target relation's type.
    */
  trait ToManyRelationship[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ] {
    self: Relationship[From, To, E, I, T, Seq] =>

    val emptyValue: Seq[T] = List()

    def actionFor(
        id: I
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Seq[T], Streaming[T], Effect.Read] =
      queryFor(id).result
    
    def include(
        includables: Includable[To, T]*
    ): ToManyWithIncludes[From, To, E, I, T] =
      new ToManyWithIncludes(this, includables)

    def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, Seq[T]], NoStream, Effect.Read] = {
      val res = innerJoinFor(query).result

      res.map(_.groupBy(_._1).map(x => (x._1, x._2.map(_._2))))
    }

  }

  /** Represents a direct (without a join-table) 'to one' relationship.
    *
    * @constructor Create a new direct (without a join-table) 'to one'
    *              relationship.
    */
  class ToOne[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Rep[Boolean]
  )(implicit
      mapping: BaseColumnType[I]
  ) extends DirectRelationship[From, To, E, I, T, Option]
      with ToOneRelationship[From, To, E, I, T]

  /** Represents a direct (without a join-table) 'to many' relationship.
    *
    * @constructor Create a new direct (without a join-table) 'to many'
    *              relationship.
    */
  class ToMany[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Rep[Boolean]
  )(implicit
      mapping: BaseColumnType[I]
  ) extends DirectRelationship[From, To, E, I, T, Seq]
      with ToManyRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to one' relationship.
    *
    * @constructor Create a new indirect (with a join-table) 'to one'
    *              relationship.
    */
  class ToOneThrough[
      From <: EntityTable[E, I],
      Through <: Table[_],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Rep[Boolean]
  )(implicit
      mapping: BaseColumnType[I]
  ) extends IndirectRelationship[From, Through, To, E, I, T, Option]
      with ToOneRelationship[From, To, E, I, T]

  /** Represents an indirect (with a join-table) 'to many' relationship.
    *
    * @constructor Create a new indirect (with a join-table) 'to many'
    *              relationship.
    */
  class ToManyThrough[
      From <: EntityTable[E, I],
      Through <: Table[_],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Rep[Boolean]
  )(implicit
      mapping: BaseColumnType[I]
  ) extends IndirectRelationship[From, Through, To, E, I, T, Seq]
      with ToManyRelationship[From, To, E, I, T]

  /** Base class for wrapping relationships.
    *
    * @constructor Creates a new wrapping relationship.
    * @param relationship The wrapped relationship.
    */
  class WrappingRelationship[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T,
      +C[_]
  ](val relationship: Relationship[From, To, E, I, T, C])
    extends Relationship[From, To, E, I, T, C]
  {
    def queryFor(id: I): Query[To, T, Seq] =
      relationship.queryFor(id)

    def queryFor(query: Query[From, E, Seq]): Query[To, T, Seq] =
      relationship.queryFor(query)

    def actionFor(id: I)(implicit ec: ExecutionContext)
    : DBIOAction[C[T], NoStream, Effect.Read] =
      relationship.actionFor(id)

    def include(
        includables: Includable[To, T]*
    ): Relationship[From, To, E, I, T, C] =
      relationship.include(includables:_*)

    def innerJoinFor(
        query: Query[From, E, Seq]
    ): Query[(From, To), (E, T), Seq] =
      relationship.innerJoinFor(query)

    def expandJoin[W <: Table[O], O](
        query: Query[(W, From), (O, E), Seq]
    ): Query[(W, To), (O, T), Seq] =
      relationship.expandJoin(query)

    def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, C[T]], NoStream, Effect.Read] =
      relationship.inclusionActionFor(query)

    val emptyValue: C[T] =
      relationship.emptyValue

    override val inclusionKey: Relationship[From, To, E, I, T, C] =
      relationship.inclusionKey
  }

  /** Wraps a 'to one' relationship and one or more includes which will be
    * eager-loaded onto any actions generated for this relationship.
    *
    * @constructor Creates a new 'to one' relationship with includes which will
    *              be eager-loaded onto any actions generated for this
    *              relationship.
    * @param relationship The 'to one' relationship into which the includes are
    *                     to be eager-loaded.
    * @param includes     The includables that are to be eager-loaded onto the
    *                     relationship.
    */
  class ToOneWithIncludes[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      relationship: Relationship[From, To, E, I, T, Option],
      val includes: Seq[Includable[To, T]]
  ) extends WrappingRelationship[From, To, E, I, T, Option](relationship) {

    override def actionFor(
        id: I
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Option[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action)((a, i) => i.includeOn(a, query))
    }

    override def include(
        includables: Includable[To, T]*
    ): ToOneWithIncludes[From, To, E, I, T] =
      new ToOneWithIncludes(relationship, includes ++ includables)

    override def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, Option[T]], NoStream, Effect.Read] = {
      val inclusionQuery = innerJoinFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result) { (a, i) =>
        i.includeOn[({type λ[α] = Map[E, α]})#λ](a, inclusionQuery.map(_._2))
      }

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.headOption)))
    }
  }

  /** Wraps a 'to many' relationship and one or more includes which will be
    * eager-loaded onto any actions generated for this relationship.
    *
    * @constructor Creates a new 'to many' relationship with includes which will
    *              be eager-loaded onto any actions generated for this
    *              relationship.
    * @param relationship The 'to many' relationship into which the includes are
    *                     to be eager-loaded.
    * @param includes     The includables that are to be eager-loaded onto the
    *                     relationship.
    */
  class ToManyWithIncludes[
      From <: EntityTable[E, I],
      To <: Table[T],
      E <: Entity[E, I],
      I,
      T
  ](
      relationship: Relationship[From, To, E, I, T, Seq],
      val includes: Seq[Includable[To, T]]
  ) extends WrappingRelationship[From, To, E, I, T, Seq](relationship) {

    override def actionFor(
        id: I
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Seq[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action.map(_.toList))((a, i) => i.includeOn(a, query))
    }

    override def include(
        includables: Includable[To, T]*
    ): ToManyWithIncludes[From, To, E, I, T] =
      new ToManyWithIncludes(relationship, includes ++ includables)

    override def inclusionActionFor(
        query: Query[From, E, Seq]
    )(implicit
        ec: ExecutionContext
    ): DBIOAction[Map[E, Seq[T]], NoStream, Effect.Read] = {
      val inclusionQuery = innerJoinFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result) { (a, i) =>
        i.includeOn[({type λ[α] = Map[E, α]})#λ](a, inclusionQuery.map(_._2))
      }

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.toList)))
    }
  }

  object DirectJoinCondition {

    /** Attempts to infer a join condition of a direct relationship (without
      * a join-table).
      *
      * The foreign keys declared on the From table and the To table will be
      * used to infer a join condition. This will fail when:
      *
      * - There are no foreign keys declared from the From table to the To table
      *   or from the To table to the From table.
      * - There is more than 1 candidate foreign key (either both From and To
      *   declare a foreign key to each other, or either of them declares more
      *   than 1 foreign key to the other).
      * - The candidate foreign key is non-standard, that is, does not point to
      *   the other table's ID column.
      *
      * @tparam From The origin table type for the join.
      * @tparam To   The target table type for the join.
      */
    def apply[From <: Table[_], To <: Table[_]]: (From, To) => Rep[Boolean] =
      macro DirectJoinConditionImpl[From, To, (From, To) => Rep[Boolean]]
  }

  object IndirectJoinCondition {

    /** Attempts to infer a join condition of an indirect relationship (with
      * a join-table).
      *
      * The foreign keys declared on the From table and the joining Through
      * table will be used to infer a join condition. This will fail when:
      *
      * - There are no foreign keys declared from the From table to the Through
      *   table or from the Through table to the From table.
      * - There is more than 1 candidate foreign key (either both From and
      *   Through declare a foreign key to each other, or either of them
      *   declares more than 1 foreign key to the other).
      * - The candidate foreign key is non-standard, that is, does not point to
      *   the other table's ID column.
      *
      * @tparam From    The origin table type for the join.
      * @tparam Through The join-table type.
      * @tparam To      The target table type for the join.
      */
    def apply[
        From <: Table[_],
        Through <: Table[_],
        To <: Table[_]
    ]: (From, (Through, To)) => Rep[Boolean] =
      macro IndirectJoinConditionImpl[
        From,
        Through,
        To,
        (From, (Through, To)) => Rep[Boolean]
      ]
  }
}

trait JoinConditionMacroImpl {
  protected def foreignKeys[
      From <: AbstractTable[_] : c.WeakTypeTag,
      To <: AbstractTable[_] : c.WeakTypeTag
  ](c: Context): Seq[c.universe.MethodSymbol] = {
    import c.universe._

    val fromType = c.weakTypeOf[From]

    fromType.members
      .filter(_.isMethod)
      .map(_.asMethod)
      .filter(_.typeSignatureIn(fromType) match {
        case NullaryMethodType(returnType) =>
          returnType <:< c.weakTypeOf[ForeignKeyQuery[To, _]]
        case _ =>
          false
      })
      .toList
  }
}

object DirectJoinConditionImpl extends JoinConditionMacroImpl {
  def apply[
      From <: AbstractTable[_] : c.WeakTypeTag,
      To <: AbstractTable[_] : c.WeakTypeTag,
      Res : c.WeakTypeTag
  ](c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From]
    val toTableType = c.weakTypeOf[To]

    val fromTableName = fromTableType.typeSymbol.asClass.fullName
    val toTableName = toTableType.typeSymbol.asClass.fullName

    val fromKeys = foreignKeys[From, To](c)
    val toKeys = foreignKeys[To, From](c)

    if (fromKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $fromTableName declares more than " +
          s"1 foreign key to $toTableName.")
    } else if (toKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $toTableName declares more than 1 " +
          s"foreign key to $fromTableName.")
    } else if (fromKeys.size + toKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $toTableName and $fromTableName " +
          s"both declare a foreign key to each other.")
    } else if (fromKeys.size + toKeys.size == 0) {
      c.abort(c.enclosingPosition,
        s"No candidate foreign key: $fromTableName and $toTableName don't " +
          s"declare any foreign keys to each other.")
    }

    if (fromKeys.size == 1) {
      c.Expr(q"""
        (f: $fromTableType, t: $toTableType) =>
          f.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[Rep[String]] === t.id.asInstanceOf[Rep[String]]
      """)
    } else {
      c.Expr(q"""
        (f: $fromTableType, t: $toTableType) =>
          f.id.asInstanceOf[Rep[String]] === t.${toKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[Rep[String]]
      """)
    }
  }
}

object IndirectJoinConditionImpl extends JoinConditionMacroImpl {
  def apply[
      From <: AbstractTable[_] : c.WeakTypeTag,
      Through <: AbstractTable[_] : c.WeakTypeTag,
      To <: AbstractTable[_] : c.WeakTypeTag,
      Res : c.WeakTypeTag
  ](c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From]
    val throughTableType = c.weakTypeOf[Through]
    val toTableType = c.weakTypeOf[To]

    val fromTableName = fromTableType.typeSymbol.asClass.fullName
    val throughTableName = throughTableType.typeSymbol.asClass.fullName

    val fromKeys = foreignKeys[From, To](c)
    val throughKeys = foreignKeys[Through, From](c)

    if (fromKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $fromTableName declares more than " +
          s"1 foreign key to $throughTableName.")
    } else if (throughKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $throughTableName declares more " +
          s"than 1 foreign key to $fromTableName.")
    } else if (fromKeys.size + throughKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $throughTableName and $fromTableName " +
          s"both declare a foreign key to each other.")
    } else if (fromKeys.size + throughKeys.size == 0) {
      c.abort(c.enclosingPosition,
        s"No candidate foreign key: $fromTableName and $throughTableName " +
          s"don't declare any foreign keys to each other.")
    }

    if (fromKeys.size == 1) {
      c.Expr(q"""
        (f: $fromTableType, t: ($throughTableType, $toTableType)) =>
          f.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[Rep[String]] === t._1.id.asInstanceOf[Rep[String]]
      """)
    } else {
      c.Expr(q"""
        (f: $fromTableType, t: ($throughTableType, $toTableType)) =>
          f.id.asInstanceOf[Rep[String]] === t._1.${throughKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[Rep[String]]
      """)
    }
  }
}
