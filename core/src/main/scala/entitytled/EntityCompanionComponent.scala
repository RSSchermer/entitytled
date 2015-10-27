package entitytled

import scala.language.experimental.macros
import scala.reflect.macros._

import annotation.implicitNotFound

import slick.lifted.AbstractTable

/** Component that groups some declarations concerning entity companion objects.
  *
  * Should be mixed in along with a [[DriverComponent]], an [[EntityComponent]],
  * a [[EntityRepositoryComponent]], a [[RelationshipComponent]] and a
  * [[RelationshipRepComponent]].
  */
trait EntityCompanionComponent {
  self: DriverComponent
    with EntityComponent
    with RelationshipComponent
    with RelationshipRepComponent
    with EntityRepositoryComponent
  =>

  import driver.api._

  /** Base class for entity companion objects.
    *
    * Extend this class with an object with the same name as the associated
    * entity to define an entity companion object. Provides entity retrieval and
    * persistance operations, as well as helpers for defining entity
    * relationships.
    *
    * @tparam T The entity's table type.
    * @tparam E The entity type.
    * @tparam I The entity's ID type.
    */
  abstract class EntityCompanion[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (implicit ev: BaseColumnType[I], tqp: TableQueryProvider[T, E])
    extends EntityRepository[T, E, I]
  {

    /** The default set of included relationship values to be included on
      * associated entity instances.
      */
    implicit val defaultIncludes: Includes[E] = Includes()

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq] = null,
      joinCondition: (T, To) => Rep[Boolean] = null,
      fromQuery: Query[T, E, Seq] = query
    ): ToOne[T, To, E, I, M] =
      macro RelationshipMacroImplementations.toOne[T, To, E, I, M, ToOne[T, To, E, I, M]]

    /** Creates a new direct (without a join-table) 'to many' relationship.
      *
      * @param toQuery       The query defining the set of possibly related rows.
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To The relationship's target table type.
      * @tparam M  The target table's element type.
      */
    protected def toMany[To <: Table[M], M]
    (toQuery: Query[To, M, Seq], joinCondition: (T, To) => Rep[Boolean])
    : ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship.
      *
      * This overload attempts to infer the toQuery parameter at compile time.
      * If it fails to do so, you may explicitly provide a toQuery to resolve
      * this.
      *
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To The relationship's target table type.
      * @tparam M  The target table's element type.
      */
    protected def toMany[To <: Table[M], M]
    (joinCondition: (T, To) => Rep[Boolean])
    (implicit provider: TableQueryProvider[To, M])
    : ToMany[T, To, E, I, M] =
      toMany[To, M](provider.tableQuery, joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship.
      *
      * This overload attempts to infer the joinCondition parameter at compile
      * time. If it fails to do so, you may explicitly provide a joinCondition
      * to resolve this.
      *
      * @param toQuery The query defining the set of possibly related rows.
      *
      * @tparam To The relationship's target table type.
      * @tparam M  The target table's element type.
      */
    protected def toMany[To <: Table[M], M]
    (toQuery: Query[To, M, Seq])
    (implicit provider: DirectJoinConditionProvider[T, To])
    : ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, provider.joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship.
      *
      * This overload attempts to infer both the toQuery and the joinCondition
      * parameter at compile time. If it fails to do so for either parameter,
      * you may explicitly provide the parameter to resolve this.
      *
      * @tparam To The relationship's target table type.
      * @tparam M  The target table's element type.
      */
    protected def toMany[To <: Table[M], M]
    (implicit tqp: TableQueryProvider[To, M], jcp: DirectJoinConditionProvider[T, To])
    : ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, tqp.tableQuery, jcp.joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship.
      *
      * @param toQuery       The query defining the set of possibly related rows.
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M]
    (toQuery: Query[(Through, To), _ <: (_, M), Seq], joinCondition: (T, (Through, To)) => Rep[Boolean])
    : ToOneThrough[T, Through, To, E, I, M] =
      new ToOneThrough[T, Through, To, E, I, M](query, toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship.
      *
      * This overload attempts to infer the toQuery parameter at compile time.
      * If it fails to do so, you may explicitly provide a toQuery to resolve
      * this.
      *
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M]
    (joinCondition: (T, (Through, To)) => Rep[Boolean])
    (implicit provider: IndirectToQueryProvider[To, Through, M])
    : ToOneThrough[T, Through, To, E, I, M] =
      new ToOneThrough[T, Through, To, E, I, M](query, provider.toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship.
      *
      * This overload attempts to infer the joinCondition parameter at compile
      * time. If it fails to do so, you may explicitly provide a joinCondition
      * to resolve this.
      *
      * @param toQuery The query defining the set of possibly related rows.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M]
    (toQuery: Query[(Through, To), _ <: (_, M), Seq])
    (implicit provider: IndirectJoinConditionProvider[T, Through, To])
    : ToOneThrough[T, Through, To, E, I, M] =
      new ToOneThrough[T, Through, To, E, I, M](query, toQuery, provider.joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship.
      *
      * This overload attempts to infer both the toQuery and the joinCondition
      * parameter at compile time. If it fails to do so for either parameter,
      * you may explicitly provide the parameter to resolve this.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M]
    (implicit tqp: IndirectToQueryProvider[To, Through, M], jcp: IndirectJoinConditionProvider[T, Through, To])
    : ToOneThrough[T, Through, To, E, I, M] =
      new ToOneThrough[T, Through, To, E, I, M](query, tqp.toQuery, jcp.joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship.
      *
      * @param toQuery       The query defining the set of possibly related rows.
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M]
    (toQuery: Query[(Through, To), _ <: (_, M), Seq], joinCondition: (T, (Through, To)) => Rep[Boolean])
    : ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship.
      *
      * This overload attempts to infer the toQuery parameter at compile time.
      * If it fails to do so, you may explicitly provide a toQuery to resolve
      * this.
      *
      * @param joinCondition The condition on which the entity query and the
      *                      toQuery are to be joined.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M]
    (joinCondition: (T, (Through, To)) => Rep[Boolean])
    (implicit provider: IndirectToQueryProvider[To, Through, M])
    : ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, provider.toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship.
      *
      * This overload attempts to infer the joinCondition parameter at compile
      * time. If it fails to do so, you may explicitly provide a joinCondition
      * to resolve this.
      *
      * @param toQuery The query defining the set of possibly related rows.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M]
    (toQuery: Query[(Through, To), _ <: (_, M), Seq])
    (implicit provider: IndirectJoinConditionProvider[T, Through, To])
    : ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, toQuery, provider.joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship.
      *
      * This overload attempts to infer both the toQuery and the joinCondition
      * parameter at compile time. If it fails to do so for either parameter,
      * you may explicitly provide the parameter to resolve this.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The joining table's type.
      * @tparam M       The target table's element type.
      */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M]
    (implicit tqp: IndirectToQueryProvider[To, Through, M], jcp: IndirectJoinConditionProvider[T, Through, To])
    : ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, tqp.toQuery, jcp.joinCondition)
  }

  /** Provides a default target query for indirect relationships.
    *
    * Intended to be instantiated through implicit materialization by it's
    * companion object.
    *
    * @tparam To      The relationship's target table type.
    * @tparam Through The join-table's type.
    * @tparam T       The target table's entity type.
    */
  @implicitNotFound("Could not infer a target query. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${Through} to ${To}, or ${To} to ${Through}.\n" +
    "- Multiple candidate foreign keys are declared for ${Through} to ${To}, or ${To} to ${Through}.\n" +
    "- The candidate foreign key is non-standard (does not point to the other tables id column).\n" +
    "To resolve this, either provide the toQuery argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  trait IndirectToQueryProvider[To <: Table[T], Through <: Table[_], T] {

    /** The provided target query for an indirect relationship. */
    val toQuery: Query[(Through, To), _ <: (_, T), Seq]
  }

  object IndirectToQueryProvider {

    /** Creates a new target query provider for indirect relationships.
      *
      * @tparam To      The relationship's target table type.
      * @tparam Through The join-table's type.
      * @tparam T       The target table's entity type.
      */
    implicit def materialize[To <: Table[T], Through <: Table[_], T]: IndirectToQueryProvider[To, Through, T] =
      macro IndirectToQueryProviderMaterializeImpl[To, Through, T, IndirectToQueryProvider[To, Through, T]]
  }

  /** Provides a default join condition for direct relationships.
    *
    * Intended to be instantiated through implicit materialization by it's
    * companion object.
    *
    * @tparam From The owner table's type.
    * @tparam To   The target table's type.
    */
  @implicitNotFound("Could not infer a join condition. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${From} to ${To}, or ${To} to ${From}.\n" +
    "- Multiple candidate foreign keys are declared for ${From} to ${To}, or ${To} to ${From}.\n" +
    "- The candidate foreign key is non-standard (does not point to the other tables id column).\n" +
    "To resolve this, either provide the joinCondition argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  trait DirectJoinConditionProvider[From <: Table[_], To <: Table[_]] {

    /** The provided default join condition. */
    val joinCondition: (From, To) => Rep[Boolean]
  }

  object DirectJoinConditionProvider {

    /** Creates a new direct join condition provider.
      *
      * @tparam From The owner table's type.
      * @tparam To   The target table's type.
      */
    implicit def materialize[From <: Table[_], To <: Table[_]]: DirectJoinConditionProvider[From, To] =
      macro DirectJoinConditionProviderMaterializeImpl[From, To, DirectJoinConditionProvider[From, To]]
  }

  /** Provides a default join condition for indirect relationships.
    *
    * Intended to be instantiated through implicit materialization by it's
    * companion object.
    *
    * @tparam From    The owner table's type.
    * @tparam Through The join-table's type.
    * @tparam To      The target table's type.
    */
  @implicitNotFound("Could not infer a join condition. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${From} to ${Through}, or ${Through} to ${From}.\n" +
    "- Multiple candidate foreign keys are declared for ${From} to ${Through}, or ${Through} to ${From}.\n" +
    "- The candidate foreign key is non-standard (does not point to the other tables id column).\n" +
    "To resolve this, either provide the joinCondition argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  trait IndirectJoinConditionProvider[From <: Table[_], Through <: Table[_], To <: Table[_]] {

    /** The provided default join condition. */
    val joinCondition: (From, (Through, To)) => Rep[Boolean]
  }

  object IndirectJoinConditionProvider {

    /** Creates a new indirect join condition provider.
      *
      * @tparam From    The owner table's type.
      * @tparam Through The join-table's type.
      * @tparam To      The target table's type.
      */
    implicit def materialize[From <: Table[_], Through <: Table[_], To <: Table[_]]
    : IndirectJoinConditionProvider[From, Through, To] =
      macro IndirectJoinConditionProviderMaterializeImpl[From, Through, To, IndirectJoinConditionProvider[From, Through, To]]
  }
}

object RelationshipMacroImplementations {
  import slick.lifted.{AbstractTable, Query, Rep}

  def toOne[
    From <: AbstractTable[_] : c.WeakTypeTag,
    To <: AbstractTable[_] : c.WeakTypeTag,
    E : c.WeakTypeTag,
    I : c.WeakTypeTag,
    T : c.WeakTypeTag,
    Res : c.WeakTypeTag
  ](c: Context)(
    toQuery: c.Expr[Query[To, T, Seq]],
    joinCondition: c.Expr[(T, To) => Rep[Boolean]],
    fromQuery: c.Expr[Query[From, E, Seq]]
  ): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From]
    val toTableType = c.weakTypeOf[To]
    val entityType = c.weakTypeOf[E]
    val idType = c.weakTypeOf[I]
    val elementType = c.weakTypeOf[T]

    c.Expr(q"""
    new ToOne[$fromTableType, $toTableType, $entityType, $idType, $elementType](
      $fromQuery,
      Option($toQuery).getOrElse(TableQuery[$toTableType]),
      Option($joinCondition).getOrElse(DirectJoinCondition[$fromTableType, $toTableType])
    )""")
  }
}

object IndirectToQueryProviderMaterializeImpl {
  def apply[To <: AbstractTable[_] : c.WeakTypeTag,
            Through <: AbstractTable[_] : c.WeakTypeTag,
            T : c.WeakTypeTag,
            Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val throughTableType = c.weakTypeOf[Through]
    val toTableType = c.weakTypeOf[To]
    val elementType = c.weakTypeOf[T]

    c.Expr(q"""
    new IndirectToQueryProvider[$toTableType, $throughTableType, $elementType] {
      val toQuery: Query[($throughTableType, $toTableType), _ <: (_, $elementType), Seq] =
        TableQuery[$throughTableType].join(TableQuery[$toTableType])
          .on(DirectJoinCondition[$throughTableType, $toTableType])
    }""")
  }
}

object DirectJoinConditionProviderMaterializeImpl {
  def apply[From <: AbstractTable[_] : c.WeakTypeTag, To <: AbstractTable[_] : c.WeakTypeTag, Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From]
    val toTableType = c.weakTypeOf[To]

    c.Expr(q"""
    new DirectJoinConditionProvider[$fromTableType, $toTableType] {
      val joinCondition: ($fromTableType, $toTableType) => Rep[Boolean] =
        DirectJoinCondition[$fromTableType, $toTableType]
    }""")
  }
}

object IndirectJoinConditionProviderMaterializeImpl {
  def apply[From <: AbstractTable[_] : c.WeakTypeTag,
            Through <: AbstractTable[_] : c.WeakTypeTag,
            To <: AbstractTable[_] : c.WeakTypeTag,
            Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From]
    val throughTableType = c.weakTypeOf[Through]
    val toTableType = c.weakTypeOf[To]

    c.Expr(q"""
    new IndirectJoinConditionProvider[$fromTableType, $throughTableType, $toTableType] {
      val joinCondition: ($fromTableType, ($throughTableType, $toTableType)) => Rep[Boolean] =
        IndirectJoinCondition[$fromTableType, $throughTableType, $toTableType]
    }""")
  }
}
