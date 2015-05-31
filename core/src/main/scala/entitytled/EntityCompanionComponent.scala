package entitytled

import scala.language.experimental.macros
import scala.reflect.macros._

import annotation.implicitNotFound

import slick.lifted.{ AbstractTable, ForeignKeyQuery }

trait EntityCompanionComponent {
  self: DriverComponent
    with EntityComponent
    with RelationshipComponent
    with RelationshipRepComponent
    with EntityRepositoryComponent
  =>

  import driver.api._

  /** Trait for entity companion objects. */
  abstract class EntityCompanion[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (implicit ev: BaseColumnType[I], tqp: TableQueryProvider[T, E])
    extends EntityRepository[T, E, I]
  {
    implicit val defaultIncludes: Includes[E] = Includes()

    /** Creates a new direct (without a join-table) 'to one' relationship */
    protected def toOne[To <: Table[M], M](
      joinCondition: (T, To) => Rep[Boolean]
    )(implicit provider: TableQueryProvider[To, M]): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, provider.tableQuery, joinCondition)

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Rep[Boolean]
    ): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, toQuery, joinCondition)

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq]
    )(implicit provider: DirectJoinConditionProvider[T, To]): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toOne[To <: Table[M], M](implicit
      tqp: TableQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To]
    ): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, tqp.tableQuery, jcp.joinCondition)

    protected def toMany[To <: Table[M], M](
      joinCondition: (T, To) => Rep[Boolean]
    )(implicit provider: TableQueryProvider[To, M]): ToMany[T, To, E, I, M] =
      toMany[To, M](provider.tableQuery, joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship */
    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Rep[Boolean]
    ): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, joinCondition)

    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq]
    )(implicit provider: DirectJoinConditionProvider[T, To]): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toMany[To <: Table[M], M](implicit
      tqp: TableQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To]
    ): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, tqp.tableQuery, jcp.joinCondition)

    /** Creates a new indirect (with a join-table) 'to one' relationship */
    protected def toOneThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Rep[Boolean]
    ): ToOneThrough[T, Through, To, E, I, M] =
      new ToOneThrough[T, Through, To, E, I, M](query, toQuery, joinCondition)

    /** Creates a new indirect (with a join-table) 'to many' relationship */
    protected def toManyThrough[To <: Table[M], Through <: Table[_], M](
      toQuery: Query[(Through, To), _ <: (_, M), Seq],
      joinCondition: (T, (Through, To)) => Rep[Boolean]
    ): ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, toQuery, joinCondition)

    protected def toManyThrough[To <: Table[M], Through <: Table[_], M](
      joinCondition: (T, (Through, To)) => Rep[Boolean]
    )(implicit provider: IndirectToQueryProvider[To, Through, M])
    : ToManyThrough[T, Through, To, E, I, M] =
      new ToManyThrough[T, Through, To, E, I, M](query, provider.toQuery, joinCondition)
  }

  @implicitNotFound("Could not infer a target query. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${Through} to ${To}, or ${To} to ${Through}.\n" +
    "- Multiple candidate foreign keys are declared for ${Through} to ${To}, or ${To} to ${Through}.\n" +
    "- The candidate foreign key is non-standard (does not point to the other tables id column).\n" +
    "To resolve this, either provide the toQuery argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  trait IndirectToQueryProvider[To <: Table[T], Through <: Table[_], T] {
    def toQuery: Query[(Through, To), _ <: (_, T), Seq]
  }

  object IndirectToQueryProvider {
    implicit def materialize[To <: Table[T], Through <: Table[_], T]: IndirectToQueryProvider[To, Through, T] =
      macro IndirectToQueryProviderMaterializeImpl[To, Through, T, IndirectToQueryProvider[To, Through, T]]
  }

  @implicitNotFound("Could not infer a join condition. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${From} to ${To}, or ${To} to ${From}.\n" +
    "- Multiple candidate foreign keys are declared for ${From} to ${To}, or ${To} to ${From}.\n" +
    "- The candidate foreign key is non-standard (does not point to the other tables id column).\n" +
    "To resolve this, either provide the joinCondition argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  trait DirectJoinConditionProvider[From <: Table[_], To <: Table[_]] {
    def joinCondition: (From, To) => Rep[Boolean]
  }

  object DirectJoinConditionProvider {
    implicit def materialize[From <: Table[_], To <: Table[_]]: DirectJoinConditionProvider[From, To] =
      macro DirectJoinConditionProviderMaterializeImpl.apply[From, To, DirectJoinConditionProvider[From, To]]
  }
}

object IndirectToQueryProviderMaterializeImpl {
  def apply[To : c.WeakTypeTag, Through : c.WeakTypeTag, T : c.WeakTypeTag, Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val throughTableType = c.weakTypeOf[Through].typeSymbol.asClass
    val toTableType = c.weakTypeOf[To].typeSymbol.asClass
    val elementType = c.weakTypeOf[T].typeSymbol.asClass

    c.Expr(q"""
    new IndirectToQueryProvider[$toTableType, $throughTableType, $elementType] {
      def toQuery: Query[($throughTableType, $toTableType), _ <: (_, $elementType), Seq] =
        TableQuery[$throughTableType].join(TableQuery[$toTableType])
          .on(DirectJoinCondition[$throughTableType, $toTableType])
    }""")
  }
}

object DirectJoinConditionProviderMaterializeImpl {
  def apply[From <: AbstractTable[_] : c.WeakTypeTag, To <: AbstractTable[_] : c.WeakTypeTag, Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From].typeSymbol.asClass
    val toTableType = c.weakTypeOf[To].typeSymbol.asClass

    c.Expr(q"""
    new DirectJoinConditionProvider[$fromTableType, $toTableType] {
      def joinCondition: ($fromTableType, $toTableType) => Rep[Boolean] =
        DirectJoinCondition[$fromTableType, $toTableType]
    }""")
  }
}
