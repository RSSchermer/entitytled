package entitytled.test.holywood

import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class EntityRepositorySpec extends FunSpec with HolywoodSpec with Matchers {

  import driver.api._

  describe("inserting Tom Cruise") {
    val newStar = Star(None, "Tom Cruise", 50)

    rollback {
      for {
        id <- Star.insert(newStar)
        stars <- Star.all.result
      } yield {
        it("should return a new MovieStarID") {
          id should not be None
        }

        it("should have added the a Tom Cruise record to the database") {
          stars.map(_.name) should contain("Tom Cruise")
        }
      }
    }
  }

  describe("updating Al Pacino's age to 74") {
    val star = Star(None, "Al Pacino", 73)

    rollback {
      for {
        id <- Star.insert(star)
        _ <- Star.update(star.copy(id = Some(id), age = 74))
        age <- Star.all.filter(_.id === id).map(_.age).result.headOption
      } yield {
        it("should have updated the age to 74") {
          age.get should be(74)
        }
      }
    }
  }

  describe("deleting Kevin Spacey") {
    rollback {
      for {
        _ <- TableQuery[Stars] ++= Seq(
          Star(None, "Meryl Streep", 65),
          Star(None, "Kevin Spacey", 55),
          Star(None, "Robert De Niro", 71)
        )

        kevinSpacey <- Star.all.filter(_.name === "Kevin Spacey").result.headOption
        _ <- Star.delete(kevinSpacey.get.id.get)
        stars <- Star.all.result
      } yield {
        it("should have reduced to number of stars to 2") {
          stars should have length 2
        }

        it("stars should not contain Kevin Spacey anymore") {
          stars.map(_.name) should not contain "Kevin Spacey"
        }
      }
    }
  }
}
