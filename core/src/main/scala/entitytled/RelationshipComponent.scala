package entitytled

import scala.language.higherKinds
import scala.language.experimental.macros
import scala.reflect.macros._

import scala.concurrent.ExecutionContext

import slick.lifted.{ AbstractTable, ForeignKeyQuery }

import scalaz._
import Scalaz._

trait RelationshipComponent {
  self: DriverComponent with EntityComponent with RelationshipRepComponent =>

  import driver.api._

  /** Implement this trait to allow including */
  trait Includable[Owner <: Table[O], O] {

    /** Include the includable on a list of instances.
      *
      * Include the includable on the given list of instances. The given
      * query must retrieve this same list of instances. */
    def includeOn[F[_]: Functor](
      action: DBIOAction[F[O], NoStream, Effect.Read],
      query: Query[Owner, O, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[F[O], NoStream, Effect.Read]
  }

  /** Represents a relationship between an owner entity and an owned relation. */
  trait Relationship[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T, +C[_]]
    extends Includable[From, E]
  {
    /** Returns a query for the owned relation for the owner entity with the
      * given id. */
    def queryFor(id: I): Query[To, T, Seq]
    
    def actionFor(id: I)(implicit ec: ExecutionContext): DBIOAction[C[T], NoStream, Effect.Read]

    val emptyValue: C[T]

    val inclusionKey: Relationship[From, To, E, I, T, C] = this

    /** Include includables for the owned relation. */
    def include(includables: Includable[To, T]*): Relationship[From, To, E, I, T, C]

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq]

    def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, C[T]], NoStream, Effect.Read]

    def includeOn[F[_] : Functor](
      action: DBIOAction[F[E], NoStream, Effect.Read],
      query: Query[From, E, Seq]
    )(implicit ec: ExecutionContext): DBIOAction[F[E], NoStream, Effect.Read] =
      action.zip(inclusionActionFor(query)).map { value =>
        value._1.map(e => e.setInclude(inclusionKey, value._2.getOrElse(e, emptyValue)))
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
    
    def include(includables: Includable[To, T]*): OneIncluding[From, To, E, I, T] =
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
    
    def include(includables: Includable[To, T]*): ManyIncluding[From, To, E, I, T] =
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
    val includes: Seq[Includable[To, T]]

    override val inclusionKey: Relationship[From, To, E, I, T, C] = relationship.inclusionKey

    def queryFor(id: I): Query[To, T, Seq] =
      relationship.queryFor(id)

    def inclusionQueryFor(query: Query[From, E, Seq]): Query[(From, To), (E, T), Seq] =
      relationship.inclusionQueryFor(query)
  }

  /** Wraps 'to one' relationships for including one or more includables. */
  class OneIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Option] with ToOneRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, T]])
    extends IncludingRelationship[From, To, E, I, T, Option](relationship)
    with ToOneRelationship[From, To, E, I, T]
  {
    override def actionFor(id: I)(implicit ec: ExecutionContext)
    : DBIOAction[Option[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action)((a, i) => i.includeOn(a, query))
    }

    override def include(includables: Includable[To, T]*): OneIncluding[From, To, E, I, T] =
      new OneIncluding(relationship, includes ++ includables)

    override def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Option[T]], NoStream, Effect.Read] = {
      val inclusionQuery = inclusionQueryFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result) { (a, i) =>
        i.includeOn[({type λ[α] = Map[E, α]})#λ](a, inclusionQuery.map(_._2))
      }

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.headOption)))
    }
  }

  /** Wraps 'to many' relationships for including one or more includables. */
  class ManyIncluding[From <: EntityTable[E, I], To <: Table[T], E <: Entity[E, I], I, T](
      override val relationship: Relationship[From, To, E, I, T, Seq] with ToManyRelationship[From, To, E, I, T],
      val includes: Seq[Includable[To, T]])
    extends IncludingRelationship[From, To, E, I, T, Seq](relationship)
    with ToManyRelationship[From, To, E, I, T]
  {
    override def actionFor(id: I)(implicit ec: ExecutionContext)
    : DBIOAction[Seq[T], NoStream, Effect.Read] = {
      val action = relationship.actionFor(id)
      val query = queryFor(id)

      includes.foldLeft(action.map(_.toList))((a, i) => i.includeOn(a, query))
    }

    override def include(includables: Includable[To, T]*): ManyIncluding[From, To, E, I, T] =
      new ManyIncluding(relationship, includes ++ includables)

    override def inclusionActionFor(query: Query[From, E, Seq])(implicit ec: ExecutionContext)
    : DBIOAction[Map[E, Seq[T]], NoStream, Effect.Read] = {
      val inclusionQuery = inclusionQueryFor(query)
      val result = inclusionQuery.result.map(_.toMap)
      val withIncludes = includes.foldLeft(result) { (a, i) =>
        i.includeOn[({type λ[α] = Map[E, α]})#λ](a, inclusionQuery.map(_._2))
      }

      withIncludes.map(_.groupBy(_._1).map(x => (x._1, x._2.values.toList)))
    }
  }

  object DirectJoinCondition {
    def apply[From <: Table[_], To <: Table[_]]: (From, To) => Rep[Boolean] =
      macro DirectJoinConditionImpl[From, To, (From, To) => Rep[Boolean]]
  }

  object IndirectJoinCondition {
    def apply[From <: Table[_], Through <: Table[_], To <: Table[_]]: (From, (Through, To)) => Rep[Boolean] =
      macro IndirectJoinConditionImpl[From, Through, To, (From, (Through, To)) => Rep[Boolean]]
  }
}

trait JoinConditionMacroImpl {
  protected def foreignKeys[From <: AbstractTable[_] : c.WeakTypeTag, To <: AbstractTable[_] : c.WeakTypeTag]
  (c: Context): Seq[c.universe.MethodSymbol] = {
    c.weakTypeOf[From].typeSymbol.typeSignature.members
      .filter(_.isMethod).map(_.asMethod)
      .filter(_.returnType <:< c.weakTypeOf[ForeignKeyQuery[To, _]])
      .toList
  }

  protected def idColumnType[T <: AbstractTable[_] : c.WeakTypeTag](c: Context): c.universe.Type = {
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

object DirectJoinConditionImpl extends JoinConditionMacroImpl {
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

    if (fromKeys.size == 1) {
      c.Expr(q"""
        (f: $fromTableType, t: $toTableType) =>
          f.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[To](c)}] === t.id
      """)
    } else {
      c.Expr(q"""
        (f: $fromTableType, t: $toTableType) =>
          f.id === t.${toKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[From](c)}]
      """)
    }
  }
}

object IndirectJoinConditionImpl extends JoinConditionMacroImpl {
  def apply[From <: AbstractTable[_] : c.WeakTypeTag,
            Through <: AbstractTable[_] : c.WeakTypeTag,
            To <: AbstractTable[_] : c.WeakTypeTag,
            Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val fromTableType = c.weakTypeOf[From].typeSymbol.asClass
    val throughTableType = c.weakTypeOf[Through].typeSymbol.asClass
    val toTableType = c.weakTypeOf[To].typeSymbol.asClass

    val fromTableName = fromTableType.fullName
    val throughTableName = throughTableType.fullName

    val fromKeys = foreignKeys[From, To](c)
    val throughKeys = foreignKeys[Through, From](c)

    if (fromKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $fromTableName declares more than 1 foreign key to $throughTableName.")
    } else if (throughKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $throughTableName declares more than 1 foreign key to $fromTableName.")
    } else if (fromKeys.size + throughKeys.size > 1) {
      c.abort(c.enclosingPosition,
        s"Multiple candidate foreign keys: $throughTableName and $fromTableName both declare a foreign key to each other.")
    } else if (fromKeys.size + throughKeys.size == 0) {
      c.abort(c.enclosingPosition,
        s"No candidate foreign key: $fromTableName and $throughTableName don't declare any foreign keys to each other.")
    }

    if (fromKeys.size == 1) {
      c.Expr(q"""
        (f: $fromTableType, t: ($throughTableType, $toTableType)) =>
          f.${fromKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[Through](c)}] === t._1.id
      """)
    } else {
      c.Expr(q"""
        (f: $fromTableType, t: ($throughTableType, $toTableType)) =>
          f.id === t._1.${throughKeys.head.name.toTermName}.fks.head.sourceColumns.asInstanceOf[${idColumnType[From](c)}]
      """)
    }
  }
}
