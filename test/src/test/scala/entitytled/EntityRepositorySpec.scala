package entitytled

import org.scalatest.{ FunSpec, Matchers }
import entitytled.holywood._
import entitytled.profile.H2Profile.driver.simple._

class EntityRepositorySpec extends FunSpec with Matchers {
  describe("the insert method") {
    Holywood.rollback { implicit session =>
      describe("inserting Tom Cruise") {
        val newStar = Star(None, "Tom Cruise", 50)
        val id = Star.insert(newStar)
        val stars = Star.list

        it("should return a new MovieStarID") {
          id should not be None
        }

        it("should have added the a Tom Cruise record to the database") {
          stars.map(_.name) should contain("Tom Cruise")
        }
      }
    }
  }

  describe("the update method") {
    Holywood.rollback { implicit session =>
      describe("updating Al Pacino's age to 74") {
        val star = Star(None, "Al Pacino", 73)
        val id = Star.insert(star)
        Star.update(star.copy(id = Some(id), age = 74))
        val age = TableQuery[Stars].filter(_.id === id).map(_.age).firstOption.get

        it("should have updated the age to 74") {
          age should be(74)
        }
      }
    }
  }

  describe("the delete method") {
    Holywood.rollback { implicit session =>
      TableQuery[Stars] ++= Seq(
        Star(None, "Meryl Streep", 65),
        Star(None, "Kevin Spacey", 55),
        Star(None, "Robert De Niro", 71)
      )

      describe("deleting Kevin Spacey") {
        val kevinSpacey = TableQuery[Stars].filter(_.name === "Kevin Spacey").firstOption.get

        Star.delete(kevinSpacey.id.get)
        val stars = Star.list

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
