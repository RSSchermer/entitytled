package entitytled.test.holywood

import org.scalatest.Inspectors._
import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipSpec extends FunSpec with HolywoodSpec with Matchers {

  import driver.api._

  rollback {
    for {
      spaceyID <- (stars returning stars.map(_.id)) += Star(None, "Kevin Spacey", 55)
      deNiroID <- (stars returning stars.map(_.id)) += Star(None, "Robert De Niro", 71)
      streepID <- (stars returning stars.map(_.id)) += Star(None, "Meryl Streep", 65)

      singerID <- (directors returning directors.map(_.id)) += Director(None, "Bryan Singer")
      scorseseID <- (directors returning directors.map(_.id)) += Director(None, "Martin Scorsese")

      usualSuspectsID <- (movies returning movies.map(_.id)) += Movie(None, "The Usual Suspects", singerID)
      goodfellasID <- (movies returning movies.map(_.id)) += Movie(None, "Goodfellas", scorseseID)

      _ <- TableQuery[MoviesStars] ++= Seq(
        (usualSuspectsID, spaceyID),
        (goodfellasID, deNiroID)
      )

      usualSuspectsDirector <- Movie.director.actionFor(usualSuspectsID)
      kevinSpaceyMovies <- Star.movies.queryFor(spaceyID).result
      kevinSpaceyMoviesCount <- Star.movies.queryFor(spaceyID).length.result
      kevinSpaceyMoviesWithDirector <- Star.movies.include(Movie.director).actionFor(spaceyID)
    } yield {
      describe("direct relationships") {
        describe("'to one' relationships") {
          describe("director for The Usual Suspects") {
            it("should return Bryan Singer") {
              usualSuspectsDirector.get.name should be("Bryan Singer")
            }
          }
        }
      }

      describe("indirect relationships") {
        describe("'to many' relationships") {
          describe("movies for Kevin Spacey") {
            it("should return 1 movie") {
              kevinSpaceyMoviesCount should be(1)
            }

            it("should contain The Usual Suspects") {
              kevinSpaceyMovies.map(_.title) should contain("The Usual Suspects")
            }
          }

          describe("with director eager-loading") {
            it("should have fetched a director for all movies") {
              forAll(kevinSpaceyMoviesWithDirector) { movie => movie.director shouldBe a[OneFetched[_, _, _]] }
            }

            it("should have fetched Bryan Singer for The Usual Suspects") {
              val usualSuspects = kevinSpaceyMoviesWithDirector.find(_.title == "The Usual Suspects").get

              usualSuspects.director.getValue.get.name should be("Bryan Singer")
            }
          }
        }
      }
    }
  }
}
