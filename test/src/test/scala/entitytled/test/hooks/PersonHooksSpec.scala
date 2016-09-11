package entitytled.test.hooks

import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class PersonHooksSpec extends FunSpec with HooksSpec with Matchers {

  import driver.api._

  rollback {
    val person = Person(None, "John")

    for {
      id <- Person.insert(person)
      _ <- Person.update(person.copy(id = Some(id), name = "Bob"))
      _ <- Person.delete(id)
      counter <- persistenceHooksCounters.filter(_.personID === id).result.headOption
    } yield {
      describe("persistence hook counter") {
        it("should have executed the after insert hook once") {
          counter.map(_.afterInsertCount) should be(Some(1))
        }

        it("should have executed the before update hook once") {
          counter.map(_.beforeUpdateCount) should be(Some(1))
        }

        it("should have executed the after update hook once") {
          counter.map(_.afterUpdateCount) should be(Some(1))
        }

        it("should have executed the after save hook twice") {
          counter.map(_.afterSaveCount) should be(Some(2))
        }

        it("should have executed the before delete hook once") {
          counter.map(_.beforeDeleteCount) should be(Some(1))
        }

        it("should have executed the after delete hook once") {
          counter.map(_.afterDeleteCount) should be(Some(1))
        }
      }
    }
  }
}
