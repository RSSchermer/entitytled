package entitytled.test.hooks.model

import entitytled.Entitytled

trait PersonHooksCounterComponent {
  self: Entitytled =>
  
  import driver.api._

  case class PersonHooksCounter(
      personID: Long,
      beforeUpdateCount: Int = 0,
      afterInsertCount: Int = 0,
      afterUpdateCount: Int = 0,
      afterSaveCount: Int = 0,
      beforeDeleteCount: Int = 0,
      afterDeleteCount: Int = 0)
  
  class PersonHooksCounters(tag: Tag)
    extends Table[PersonHooksCounter](tag, "PERSON_HOOKS_COUNTERS")
  {
    def personID = column[Long]("person_id", O.PrimaryKey)
    def beforeUpdateCount = column[Int]("before_update_count")
    def afterInsertCount = column[Int]("after_insert_count")
    def afterUpdateCount = column[Int]("after_update_count")
    def afterSaveCount = column[Int]("after_save_count")
    def beforeDeleteCount = column[Int]("before_delete_count")
    def afterDeleteCount = column[Int]("after_delete_count")
    
    def * = (
        personID,
        beforeUpdateCount,
        afterInsertCount,
        afterUpdateCount,
        afterSaveCount,
        beforeDeleteCount,
        afterDeleteCount
      ) <> ((PersonHooksCounter.apply _).tupled, PersonHooksCounter.unapply)
  }
}
