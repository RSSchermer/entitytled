package entitytled

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.reflect.macros._

import entitytled.exception.MissingIDException

trait EntityRepositoryComponent {
  self: DriverComponent with EntityComponent with EntityBuilderComponent =>

  import driver.api._

  /** Repository class for managing the retrieval and persistence of entities. */
  class EntityRepository[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (implicit ev: BaseColumnType[I], tqp: TableQueryProvider[T])
    extends AbstractEntityCollectionBuilder[T, E, I]
  {
    val query: Query[T, E, Seq] = tqp.tableQuery.asInstanceOf[Query[T, E, Seq]]

    /** Inserts a given entity instance into the database. */
    def insert(instance: E)(implicit ec: ExecutionContext): DBIOAction[I, NoStream, Effect.Write] = {
      val modifiedInstance = beforeInsert(beforeSave(instance))

      val action = instance.id match {
        case Some(id) =>
          (query += modifiedInstance).map(x => id)
        case _ =>
          query returning query.map(_.id) += modifiedInstance
      }

      action.map { id =>
        afterInsert(id, instance)
        afterSave(id, instance)

        id
      }
    }

    /** Updates the database record for the given entity instance. */
    def update(instance: E)(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Write] =
      instance.id match {
        case Some(id) =>
          val modifiedInstance = beforeUpdate(beforeSave(instance))
          one(id).query.update(modifiedInstance).map { _ =>
            afterUpdate(id, instance)
            afterSave(id, instance)
          }
        case _ => DBIO.failed(new MissingIDException)
      }

    /** Removes the entity with the given ID from the database. */
    def delete(id: I)(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Write] = {
      beforeDelete(id)

      one(id).query.delete.map(_ => afterDelete(id))
    }

    /** May be overriden to specify actions that should be taken before
      * inserting a new entity. */
    protected def beforeInsert(instance: E)(implicit ec: ExecutionContext): E = instance

    /** May be overriden to specify actions that should be taken before
      * updating an entity. */
    protected def beforeUpdate(instance: E)(implicit ec: ExecutionContext): E = instance

    /** May be overriden to specify actions that should be taken either before
      * inserting, or before updating an entity. */
    protected def beforeSave(instance: E)(implicit ec: ExecutionContext): E = instance

    /** May be overriden to specify actions that should be taken before
      * deleting an entity. */
    protected def beforeDelete(key: I)(implicit ec: ExecutionContext): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * inserting a new entity. */
    protected def afterInsert(key: I, instance: E)(implicit ec: ExecutionContext): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * updating an entity. */
    protected def afterUpdate(key: I, instance: E)(implicit ec: ExecutionContext): Unit = ()

    /** May be overriden to specify actions that should be taken either after
      * inserting, or after updating an entity. */
    protected def afterSave(key: I, instance: E)(implicit ec: ExecutionContext): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * deleting an entity. */
    protected def afterDelete(key: I)(implicit ec: ExecutionContext): Unit = ()
  }

  trait TableQueryProvider[T <: Table[_]] {
    def tableQuery: Query[T, _, Seq]
  }

  object TableQueryProvider {
    implicit def materialize[T <: Table[_]]: TableQueryProvider[T] =
      macro MaterializeTableQueryProviderImpl.apply[T]
  }
}

object MaterializeTableQueryProviderImpl {
  def apply[T : c.WeakTypeTag](c: Context) = {
    import c.universe._

    val tableType = c.weakTypeOf[T].typeSymbol.asClass

    c.Expr(q"""
    new TableQueryProvider[$tableType] {
      def tableQuery: Query[$tableType, $tableType#TableElementType, Seq] = TableQuery[$tableType]
    }""")
  }
}
