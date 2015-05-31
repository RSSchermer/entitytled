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
    )(implicit provider: DirectToQueryProvider[To, M]): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, provider.toQuery, joinCondition)

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Rep[Boolean]
    ): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, toQuery, joinCondition)

    protected def toOne[To <: Table[M], M](
      toQuery: Query[To, M, Seq]
    )(implicit provider: DirectJoinConditionProvider[T, To, I]): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toOne[To <: Table[M], M](implicit
      tqp: DirectToQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To, I]
    ): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, tqp.toQuery, jcp.joinCondition)

    protected def toMany[To <: Table[M], M](
      joinCondition: (T, To) => Rep[Boolean]
    )(implicit provider: DirectToQueryProvider[To, M]): ToMany[T, To, E, I, M] =
      toMany[To, M](provider.toQuery, joinCondition)

    /** Creates a new direct (without a join-table) 'to many' relationship */
    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq],
      joinCondition: (T, To) => Rep[Boolean]
    ): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, joinCondition)

    protected def toMany[To <: Table[M], M](
      toQuery: Query[To, M, Seq]
    )(implicit provider: DirectJoinConditionProvider[T, To, I]): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toMany[To <: Table[M], M](implicit
      tqp: DirectToQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To, I]
    ): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, tqp.toQuery, jcp.joinCondition)

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
  }

  @implicitNotFound("Could not infer a target query, please specify the toQuery argument explicitly.")
  trait DirectToQueryProvider[To <: Table[T], T] {
    def toQuery: Query[To, T, Seq]
  }

  object DirectToQueryProvider {
    implicit def materialize[To <: Table[T], T]: DirectToQueryProvider[To, T] =
      macro DirectToQueryProviderMaterializeImpl.apply[To, T, DirectToQueryProvider[To, T]]
  }

  @implicitNotFound("Could not infer a join condition. This can be for the following reasons:\n" +
    "- No candidate foreign keys are declared for either ${From} to ${To}, or ${To} to ${From}.\n" +
    "- Multiple candidate foreign keys are declared for ${From} to ${To}, or ${To} to ${From}.\n" +
    "To resolve this, either specify the joinCondition argument explicitly, " +
    "or make sure exactly 1 candidate foreign key is defined.")
  abstract class DirectJoinConditionProvider[From <: EntityTable[_, I], To <: Table[_], I] {
    def joinCondition(implicit ev: BaseColumnType[I]): (From, To) => Rep[Boolean]
  }

  object DirectJoinConditionProvider {
    implicit def materialize[From <: EntityTable[_, I], To <: Table[_], I]: DirectJoinConditionProvider[From, To, I] =
      macro DirectJoinConditionProviderMaterializeImpl.apply[From, To, I, DirectJoinConditionProvider[From, To, I]]
  }
}

object DirectToQueryProviderMaterializeImpl {
  def apply[To : c.WeakTypeTag, T : c.WeakTypeTag, Res : c.WeakTypeTag](c: Context): c.Expr[Res] = {
    import c.universe._

    val toTableType = c.weakTypeOf[To].typeSymbol.asClass
    val elementType = c.weakTypeOf[T].typeSymbol.asClass

    c.Expr(q"""
    new DirectToQueryProvider[$toTableType, $elementType] {
      def toQuery: Query[$toTableType, $elementType, Seq] = TableQuery[$toTableType]
    }""")
  }
}

object DirectJoinConditionProviderMaterializeImpl {
  def apply[From <: AbstractTable[_], To <: AbstractTable[_], I, Res](c: Context)(implicit
    ev1: c.WeakTypeTag[From],
    ev2: c.WeakTypeTag[To],
    ev3: c.WeakTypeTag[I],
    ev4: c.WeakTypeTag[Res])
  : c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From].typeSymbol.asClass
    val toTableType = c.weakTypeOf[To].typeSymbol.asClass
    val fromIdType = c.weakTypeOf[I].typeSymbol.asClass

    val fromKeys = c.weakTypeOf[From].typeSymbol.typeSignature.members
      .filter(_.isMethod).map(_.asMethod)
      .filter(_.returnType <:< c.weakTypeOf[ForeignKeyQuery[To, _]])
    val toKeys = c.weakTypeOf[To].typeSymbol.typeSignature.members
      .filter(_.isMethod).map(_.asMethod)
      .filter(_.returnType <:< c.weakTypeOf[ForeignKeyQuery[From, _]])

    if (fromKeys.size > 1) {
      c.abort(c.enclosingPosition, "Could not infer join condition: FromTable declared more than 1 foreign key to ToTable.")
    } else if (toKeys.size > 1) {
      c.abort(c.enclosingPosition, "Could not infer join condition: ToTable declared more than 1 foreign key to FromTable.")
    } else if (fromKeys.size + toKeys.size > 1) {
      c.abort(c.enclosingPosition, "Could not infer join condition: ToTable and FromTable both declare foreign keys to each other.")
    } else if (fromKeys.size + toKeys.size == 0) {
      c.abort(c.enclosingPosition, "Could not infer join condition: neither FromTable nor ToTable declares a foreign key to the other table.")
    }

    if (fromKeys.size == 1) {
      val idField = c.weakTypeOf[To].typeSymbol.typeSignature.members
        .filter(_.isMethod).map(_.asMethod).find(_.name.toString == "id")

      if (idField.isEmpty) {
        c.abort(c.enclosingPosition, "Could not find `id` method on ToTable.")
      }

      val idType = idField.get.returnType

      c.Expr(q"""
      new DirectJoinConditionProvider[$fromTableType, $toTableType, $fromIdType] {
        def joinCondition(implicit ev: BaseColumnType[$fromIdType]): ($fromTableType, $toTableType) => Rep[Boolean] =
          _.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[$idType] === _.id
      }""")
    } else {
      val idField = c.weakTypeOf[From].typeSymbol.typeSignature.members
        .filter(_.isMethod).map(_.asMethod).find(_.name.toString == "id")

      if (idField.isEmpty) {
        c.abort(c.enclosingPosition, "Could not find `id` method on ToTable.")
      }

      val idType = idField.get.returnType

      c.Expr(q"""
      new DirectJoinConditionProvider[$fromTableType, $toTableType, $fromIdType] {
        def joinCondition(implicit ev: BaseColumnType[$fromIdType]): ($fromTableType, $toTableType) => Rep[Boolean] =
          _.id === _.${toKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[$idType]
      }""")
    }
  }
}
