package entitytled.test.hooks.model

import entitytled.Entitytled

import scala.concurrent.ExecutionContext

trait PersonComponent {
  self: Entitytled with PersonHooksCounterComponent =>

  import driver.api._

  case class Person(id: Option[Long], name: String) extends Entity[Person, Long]

  object Person extends EntityCompanion[Persons, Person, Long] {
    override protected def afterInsert(id: Long, instance: Person)
                                      (implicit ec: ExecutionContext)
    : DBIO[Unit] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(afterInsertCount = c.afterInsertCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(id, afterInsertCount = 1)
      }
    } yield ()

    override protected def beforeUpdate(instance: Person)
                                       (implicit ec: ExecutionContext)
    : DBIO[Person] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === instance.id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(beforeUpdateCount = c.beforeUpdateCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(instance.id.get, beforeUpdateCount = 1)
      }
    } yield instance

    override protected def afterUpdate(id: Long, instance: Person)
                                      (implicit ec: ExecutionContext)
    : DBIO[Unit] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(afterUpdateCount = c.afterUpdateCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(id, afterUpdateCount = 1)
      }
    } yield ()

    override protected def afterSave(id: Long, instance: Person)
                                    (implicit ec: ExecutionContext)
    : DBIO[Unit] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(afterSaveCount = c.afterSaveCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(id, afterSaveCount = 1)
      }
    } yield ()

    override protected def beforeDelete(id: Long)(implicit ec: ExecutionContext): DBIO[Unit] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(beforeDeleteCount = c.beforeDeleteCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(id, beforeDeleteCount = 1)
      }
    } yield ()

    override protected def afterDelete(id: Long)(implicit ec: ExecutionContext): DBIO[Unit] = for {
      counter <- TableQuery[PersonHooksCounters].filter(_.personID === id).result.headOption
      _ <- counter match {
        case Some(c) =>
          TableQuery[PersonHooksCounters].filter(_.personID === c.personID)
            .update(c.copy(afterDeleteCount = c.afterDeleteCount + 1))
        case None =>
          TableQuery[PersonHooksCounters] += PersonHooksCounter(id, afterDeleteCount = 1)
      }
    } yield ()
  }

  class Persons(tag: Tag) extends EntityTable[Person, Long](tag, "PERSONS") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")

    def * = (id.?, name) <> ((Person.apply _).tupled, Person.unapply)
  }
}
