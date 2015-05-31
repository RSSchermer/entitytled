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
    )(implicit provider: DirectJoinConditionProvider[T, To]): ToOne[T, To, E, I, M] =
      new ToOne[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toOne[To <: Table[M], M](implicit
      tqp: DirectToQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To]
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
    )(implicit provider: DirectJoinConditionProvider[T, To]): ToMany[T, To, E, I, M] =
      new ToMany[T, To, E, I, M](query, toQuery, provider.joinCondition)

    protected def toMany[To <: Table[M], M](implicit
      tqp: DirectToQueryProvider[To, M],
      jcp: DirectJoinConditionProvider[T, To]
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
  def apply[From <: AbstractTable[_] : c.WeakTypeTag, To <: AbstractTable[_] : c.WeakTypeTag, Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From].typeSymbol.asClass
    val toTableType = c.weakTypeOf[To].typeSymbol.asClass

    val fromTableName = fromTableType.fullName
    val toTableName = toTableType.fullName

    val fromKeys = foreignKeys[From, To](c)
    val toKeys = foreignKeys[To, From](c)

    if (fromKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $fromTableName declares more than 1 foreign key to $toTableName.")
    } else if (toKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $toTableName declares more than 1 foreign key to $fromTableName.")
    } else if (fromKeys.size + toKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $toTableName and $fromTableName both declare a foreign key to each other.")
    } else if (fromKeys.size + toKeys.size == 0) {
      c.abort(c.enclosingPosition,
        s"No candidate foreign key: $fromTableName and $toTableName don't declare any foreign keys to each other.")
    }

    val joinCondition = if (fromKeys.size == 1) {
      q"""_.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[To](c)}] === _.id"""
    } else {
      q"""_.id === _.${toKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[From](c)}]"""
    }

    c.Expr(q"""
    new DirectJoinConditionProvider[$fromTableType, $toTableType] {
      def joinCondition: ($fromTableType, $toTableType) => Rep[Boolean] = $joinCondition
    }""")
  }

  private def foreignKeys[From <: AbstractTable[_] : c.WeakTypeTag, To <: AbstractTable[_] : c.WeakTypeTag]
  (c: Context): Seq[c.universe.MethodSymbol] = {
    c.weakTypeOf[From].typeSymbol.typeSignature.members
      .filter(_.isMethod).map(_.asMethod)
      .filter(_.returnType <:< c.weakTypeOf[ForeignKeyQuery[To, _]])
      .toList
  }

  private def idColumnType[T <: AbstractTable[_] : c.WeakTypeTag](c: Context): c.universe.Type = {
    val idField = c.weakTypeOf[T].typeSymbol.typeSignature.members
      .filter(_.isMethod).map(_.asMethod)
      .find(_.name.toString == "id")

    if (idField.isEmpty) {
      val tableName = c.weakTypeOf[T].typeSymbol.asClass.fullName
      c.abort(c.enclosingPosition, s"Could not find an `id` member on $tableName.")
    }

    idField.get.returnType
  }
}
