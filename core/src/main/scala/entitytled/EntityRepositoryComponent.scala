package entitytled

import entitytled.exception.MissingIDException

trait EntityRepositoryComponent {
  self: DriverComponent with TableComponent with EntityBuilderComponent =>

  import driver.simple._

  abstract class EntityRepository[T <: EntityTable[E], E <: Entity](implicit ev: BaseColumnType[E#IdType])
    extends AbstractEntityCollectionBuilder[T, E]
  {
    def insert(instance: E)(implicit s: Session): E#IdType = {
      s.withTransaction {
        val modifiedInstance = beforeInsert(beforeSave(instance))

        val key = query returning query.map(_.id) += modifiedInstance

        afterInsert(key, instance)
        afterSave(key, instance)

        key
      }
    }

    def update(instance: E)(implicit s: Session): Unit = instance.id match {
      case Some(id) =>
        s.withTransaction {
          val modifiedInstance = beforeUpdate(beforeSave(instance))

          query.filter(_.id === id.asInstanceOf[E#IdType]).update(modifiedInstance)
          afterUpdate(id, instance)
          afterSave(id, instance)
        }
      case _ => throw new MissingIDException
    }

    def delete(id: E#IdType)(implicit s: Session): Unit = {
      s.withTransaction {
        beforeDelete(id)
        query.filter(_.id === id).delete
        afterDelete(id)
      }
    }

    protected def beforeInsert(instance: E)(implicit s: Session): E = instance

    protected def beforeUpdate(instance: E)(implicit s: Session): E = instance

    protected def beforeSave(instance: E)(implicit s: Session): E = instance

    protected def beforeDelete(key: E#IdType)(implicit s: Session): Unit = ()

    protected def afterInsert(key: E#IdType, instance: E)(implicit s: Session): Unit = ()

    protected def afterUpdate(key: E#IdType, instance: E)(implicit s: Session): Unit = ()

    protected def afterSave(key: E#IdType, instance: E)(implicit s: Session): Unit = ()

    protected def afterDelete(key: E#IdType)(implicit s: Session): Unit = ()
  }
}
