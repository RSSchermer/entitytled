package entitytled

import slick.profile.FixedSqlStreamingAction

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions
import scala.language.experimental.macros
import scala.reflect.macros._

import entitytled.exception.MissingIDException

/** Component grouping some declarations regarding entity repositories.
  *
  * Needs to be mixed in along with a [[DriverComponent]], an [[EntityComponent]]
  * and a [[EntityActionBuilderComponent]].
  */
trait EntityRepositoryComponent {
  self: DriverComponent with EntityComponent with EntityActionBuilderComponent =>

  import driver.api._

  /** Repository class for managing the retrieval and persistence of entities.
    *
    * @constructor Creates a new entity repository.
    *
    * @tparam T The entity's table type.
    * @tparam E The entity type.
    * @tparam I The entity's ID type.
    */
  class EntityRepository[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (implicit ev: BaseColumnType[I], tqp: TableQueryProvider[T, E])
  {
    val query: Query[T, E, Seq] = tqp.tableQuery

    val all: Query[T, E, Seq] = query

    val result: FixedSqlStreamingAction[Seq[E], E, Effect.Read] = query.result

    def one(id: I): EntityInstanceActionBuilder[T, E] =
      new EntityInstanceActionBuilder[T, E](query.filter(_.id === id).take(1))

    /** Returns a database action for inserting the given entity instance into
      * the database.
      *
      * @param instance The entity instance to be inserted.
      * @param ec       The execution context in which the action is to be
      *                 build.
      */
    def insert(instance: E)(implicit ec: ExecutionContext): DBIO[I] =
      beforeSave(instance).flatMap(beforeInsert).flatMap { i => (i.id match {
          case Some(id) =>
            (query += i).map(x => id)
          case _ =>
            query returning query.map(_.id) += i
        }).flatMap(id => afterSave(id, i) >> afterInsert(id, i) >> DBIO.successful(id))
      }

    /** Returns a database action for updating the database record for the given
      * entity instance.
      *
      * @param instance The entity instance to be updated.
      * @param ec       The execution context in which the action is to be
      *                 build.
      */
    def update(instance: E)(implicit ec: ExecutionContext): DBIO[Unit] =
      instance.id match {
        case Some(id) =>
          beforeSave(instance).flatMap(beforeUpdate).flatMap { i =>
            query.filter(_.id === id).update(i) >> afterSave(id, i) >> afterUpdate(id, i)
          }
        case _ => DBIO.failed(new MissingIDException)
      }

    /** Returns a database action for deleteing the entity with the given ID
      * from the database.
      *
      * @param id The ID of the entity to be deleted.
      * @param ec The execution context in which the action is to be build.
      */
    def delete(id: I)(implicit ec: ExecutionContext): DBIO[Unit] =
      beforeDelete(id) >> query.filter(_.id === id).delete >> afterDelete(id)

    /** May be overriden to specify actions that should be taken before
      * inserting a new entity.
      *
      * @param instance The entity instance that is to be insterted.
      */
    protected def beforeInsert(instance: E): DBIO[E] = DBIO.successful(instance)

    /** May be overriden to specify actions that should be taken before
      * updating an entity.
      *
      * @param instance The entity instance that is to be updated.
      */
    protected def beforeUpdate(instance: E): DBIO[E] = DBIO.successful(instance)

    /** May be overriden to specify actions that should be taken either before
      * inserting, or before updating an entity.
      *
      * @param instance The entity instance that is to be saved.
      */
    protected def beforeSave(instance: E): DBIO[E] = DBIO.successful(instance)

    /** May be overriden to specify actions that should be taken before
      * deleting an entity.
      *
      * @param id The ID of entity instance that is to be deleted.
      */
    protected def beforeDelete(id: I): DBIO[Unit] = DBIO.successful(())

    /** May be overriden to specify actions that should be taken after
      * inserting a new entity.
      *
      * @param id       The ID of entity instance that was inserted.
      * @param instance The entity instance that was inserted.
      */
    protected def afterInsert(id: I, instance: E): DBIO[Unit] = DBIO.successful(())

    /** May be overriden to specify actions that should be taken after
      * updating an entity.
      *
      * @param id       The ID of entity instance that was updated.
      * @param instance The entity instance that was updated.
      */
    protected def afterUpdate(id: I, instance: E): DBIO[Unit] = DBIO.successful(())

    /** May be overriden to specify actions that should be taken either after
      * inserting, or after updating an entity.
      *
      * @param id       The ID of entity instance that was saved.
      * @param instance The entity instance that was saved.
      */
    protected def afterSave(id: I, instance: E): DBIO[Unit] = DBIO.successful(())

    /** May be overriden to specify actions that should be taken after
      * deleting an entity.
      *
      * @param id The ID of entity instance that was deleted.
      */
    protected def afterDelete(id: I): DBIO[Unit] = DBIO.successful(())
  }

  /** Provides a table query for table T.
    *
    * Intended to be instantiated through implicit materialization by it's
    * companion object.
    *
    * @tparam T Table type for which the table query is provided.
    * @tparam M Element type of the table for which the table query is provided.
    */
  trait TableQueryProvider[T <: Table[M], M] {

    /** The provided table query. */
    val tableQuery: Query[T, M, Seq]
  }

  object TableQueryProvider {

    /** Materializes a new table query provider for table T.
      *
      * @tparam T Table type for which the table query is provided.
      * @tparam M Element type of the table for which the table query is
      *           provided.
      */
    implicit def materialize[T <: Table[M], M]: TableQueryProvider[T, M] =
      macro MaterializeTableQueryProviderImpl.apply[T, M, TableQueryProvider[T, M]]
  }
}

trait EntityRepositoryActionBuilderConversionComponent {
  self: EntityComponent
    with EntityRepositoryComponent
    with EntityActionBuilderComponent
  =>

  implicit def repositoryToEntityActionBuilder[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (repository: EntityRepository[T, E, I]): EntityCollectionActionBuilder[T, E] =
    new EntityCollectionActionBuilder[T, E](repository.query)
}

trait EntityRepositoryConversionsComponent
  extends EntityRepositoryActionBuilderConversionComponent
{
  self: DriverComponent
    with EntityComponent
    with EntityRepositoryComponent
    with EntityActionBuilderComponent
  =>

  import driver.api._

  implicit def repositoryToQuery[T <: EntityTable[E, I], E <: Entity[E, I], I]
  (repository: EntityRepository[T, E, I]): Query[T, E, Seq] =
    repository.query
}

object MaterializeTableQueryProviderImpl {
  def apply[T : c.WeakTypeTag, M : c.WeakTypeTag, Res : c.WeakTypeTag]
  (c: Context): c.Expr[Res] = {
    import c.universe._

    val tableType = c.weakTypeOf[T].typeSymbol.asClass
    val elementType = c.weakTypeOf[M].typeSymbol.asClass

    c.Expr(q"""
    new TableQueryProvider[$tableType, $elementType] {
      val tableQuery: Query[$tableType, $elementType, Seq] =
        TableQuery[$tableType]
    }""")
  }
}
