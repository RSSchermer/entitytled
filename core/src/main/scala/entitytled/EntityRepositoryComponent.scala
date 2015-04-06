package entitytled

import entitytled.exception.MissingIDException

trait EntityRepositoryComponent {
  self: DriverComponent with EntityComponent with EntityBuilderComponent =>

  import driver.simple._

  /** Repository class for managing the retrieval and persistence of entities. */
  abstract class EntityRepository[T <: EntityTable[E, I], E <: Entity[E, I], I](implicit ev: BaseColumnType[I])
    extends AbstractEntityCollectionBuilder[T, E, I]
  {
    /** Inserts a given entity instance into the database. */
    def insert(instance: E)(implicit s: Session): I = {
      s.withTransaction {
        val modifiedInstance = beforeInsert(beforeSave(instance))

        val key = instance.id match {
          case Some(id) =>
            query += modifiedInstance
            id
          case _ =>
            query returning query.map(_.id) += modifiedInstance
        }

        afterInsert(key, instance)
        afterSave(key, instance)

        key
      }
    }

    /** Updates the database record for the given entity instance. */
    def update(instance: E)(implicit s: Session): Unit = instance.id match {
      case Some(id) =>
        s.withTransaction {
          val modifiedInstance = beforeUpdate(beforeSave(instance))

          query.filter(_.id === id.asInstanceOf[I]).update(modifiedInstance)
          afterUpdate(id, instance)
          afterSave(id, instance)
        }
      case _ => throw new MissingIDException
    }

    /** Removes the entity with the given ID from the database. */
    def delete(id: I)(implicit s: Session): Unit = {
      s.withTransaction {
        beforeDelete(id)
        query.filter(_.id === id).delete
        afterDelete(id)
      }
    }

    /** May be overriden to specify actions that should be taken before
      * inserting a new entity. */
    protected def beforeInsert(instance: E)(implicit s: Session): E = instance

    /** May be overriden to specify actions that should be taken before
      * updating an entity. */
    protected def beforeUpdate(instance: E)(implicit s: Session): E = instance

    /** May be overriden to specify actions that should be taken either before
      * inserting, or before updating an entity. */
    protected def beforeSave(instance: E)(implicit s: Session): E = instance

    /** May be overriden to specify actions that should be taken before
      * deleting an entity. */
    protected def beforeDelete(key: I)(implicit s: Session): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * inserting a new entity. */
    protected def afterInsert(key: I, instance: E)(implicit s: Session): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * updating an entity. */
    protected def afterUpdate(key: I, instance: E)(implicit s: Session): Unit = ()

    /** May be overriden to specify actions that should be taken either after
      * inserting, or after updating an entity. */
    protected def afterSave(key: I, instance: E)(implicit s: Session): Unit = ()

    /** May be overriden to specify actions that should be taken after
      * deleting an entity. */
    protected def afterDelete(key: I)(implicit s: Session): Unit = ()
  }
}
