package entitytled

import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.Inspectors._
import entitytled.holywood._
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class EntityBuilderSpec extends FunSpec with Matchers {
  Holywood.rollback { implicit session =>
    val stars = TableQuery[Stars]
    val movies = TableQuery[Movies]

    val spacey = (stars returning stars.map(_.id)) += Star(None, "Kevin Spacey", 55)
    val deNiro = (stars returning stars.map(_.id)) += Star(None, "Robert De Niro", 71)
    val streep = (stars returning stars.map(_.id)) += Star(None, "Meryl Streep", 65)

    val usualSuspects = (movies returning movies.map(_.id)) += Movie(None, "The Usual Suspects")
    val goodfellas = (movies returning movies.map(_.id)) += Movie(None, "Goodfellas")

    TableQuery[MoviesStars] ++= Seq(
      (usualSuspects, spacey),
      (goodfellas, deNiro)
    )

    describe("EntityCollectionBuilder") {
      describe("the list method") {
        val stars = Star.list

        it("should returns all movie stars") {
          stars should have length 3
          stars.map(_.name) should contain theSameElementsAs Seq("Meryl Streep", "Kevin Spacey", "Robert De Niro")
        }

        describe("with side-loading") {
          val starsWithMovies = Star.include(Star.movies)
          val stars = starsWithMovies.list

          it("should have fetched the movies for each star listed") {
            forAll (stars) { star => star.movies shouldBe a [ManyFetched[_, _]] }
          }

          it("should have fetched The Usual Suspects for Kevin Spacey") {
            stars.find(_.name == "Kevin Spacey").get.movies.get.map(_.title) should contain ("The Usual Suspects")
          }

          it("should not have fetched The Usual Suspects for Robert De Niro") {
            stars.find(_.name == "Robert De Niro").get.movies.get.map(_.title) should not contain "The Usual Suspects"
          }
        }
      }

      describe("the filter method") {
        describe("the resulting list after filtering for only 65 and older") {
          val filtered = Star.filter(_.age >= 65)
          val stars = filtered.list

          it("should not contain Kevin Spacey") {
            stars.map(_.name) should not contain "Kevin Spacey"
          }

          it("should contain Streep and De Niro") {
            stars.map(_.name) should contain allOf("Meryl Streep", "Robert De Niro")
          }

          describe("the resulting list after chaining an additional filter for 70 and younger") {
            val doubleFiltered = filtered.filter(_.age <= 70).list

            it("should not contain Robert De Niro") {
              doubleFiltered.map(_.name) should not contain "Robert De Niro"
            }

            it("should contain Meryl Streep") {
              doubleFiltered.map(_.name) should contain ("Meryl Streep")
            }
          }
        }
      }

      describe("the sortBy method") {
        describe("stars sorted in ascending age") {
          val stars = Star.sortBy(_.age.asc).list

          it("should have retrieved Kevin Spacey as the first entry") {
            stars.headOption.get.name should be ("Kevin Spacey")
          }

          it("should have retrieved Robert De Niro as the last entry") {
            stars.lastOption.get.name should be ("Robert De Niro")
          }
        }

        describe("stars sorted in descending age") {
          val stars = Star.sortBy(_.age.desc).list

          it("should have retrieved Robert De Niro as the first entry") {
            stars.headOption.get.name should be ("Robert De Niro")
          }

          it("should have retrieved Kevin Spacey as the last entry") {
            stars.lastOption.get.name should be ("Kevin Spacey")
          }
        }
      }
    }
  }
}
