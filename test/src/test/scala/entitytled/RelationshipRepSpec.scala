package entitytled

import org.scalatest.{ FunSpec, Matchers }
import entitytled.holywood._
import entitytled.profile.H2Profile.driver.simple._

class RelationshipRepSpec extends FunSpec with Matchers {
  Holywood.rollback { implicit session =>
    val stars = TableQuery[Stars]
    val directors = TableQuery[Directors]
    val movies = TableQuery[Movies]

    val spaceyID = (stars returning stars.map(_.id)) += Star(None, "Kevin Spacey", 55)

    val singer = Director(None, "Bryan Singer")
    val singerID = (directors returning directors.map(_.id)) += singer

    val usualSuspectsID = (movies returning movies.map(_.id)) +=
      Movie(None, "The Usual Suspects", singerID)

    TableQuery[MoviesStars] += (usualSuspectsID, spaceyID)

    describe("The Usual Suspects to one director relationship rep") {
      describe("implicit conversion to Option") {
        val director = Movie.find(usualSuspectsID).get.director.get

        it("should be Bryan Singer") {
          director.name should be("Bryan Singer")
        }
      }
    }

    describe("The Usual Suspects to many stars relationship rep") {
      describe("implicit conversion to Seq") {
        val starNames = Movie.find(usualSuspectsID).get.stars.map(_.name)

        it("should contain Kevin Spacey") {
          starNames should contain theSameElementsAs Seq("Kevin Spacey")
        }
      }
    }
  }
}
