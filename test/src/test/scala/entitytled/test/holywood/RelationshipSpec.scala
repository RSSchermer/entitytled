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
      mendesID <- (directors returning directors.map(_.id)) += Director(None, "Sam Mendes")

      usualSuspectsID <- (movies returning movies.map(_.id)) += Movie(None, "The Usual Suspects", singerID)
      goodfellasID <- (movies returning movies.map(_.id)) += Movie(None, "Goodfellas", scorseseID)
      americanBeautyID <- (movies returning movies.map(_.id)) += Movie(None, "American Beauty", mendesID)

      _ <- TableQuery[MoviesStars] ++= Seq(
        (usualSuspectsID, spaceyID),
        (goodfellasID, deNiroID),
        (americanBeautyID, spaceyID)
      )

      usualSuspectsDirector <- Movie.director.actionFor(usualSuspectsID)
      kevinSpaceyMovies <- Star.movies.queryFor(spaceyID).result
      kevinSpaceyMoviesCount <- Star.movies.queryFor(spaceyID).length.result
      kevinSpaceyMoviesWithDirector <- Star.movies.include(Movie.director).actionFor(spaceyID)
      kevinSpaceyDirectors <- Star.directors.queryFor(spaceyID).result
      kevinSpaceyDirectorsCount <- Star.directors.queryFor(spaceyID).length.result
      bryanSingerStars <- Director.stars.queryFor(singerID).result
      bryanSingerStarsCount <- Director.stars.queryFor(singerID).length.result
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
            it("should return 2 movies") {
              kevinSpaceyMoviesCount should be(2)
            }

            it("should contain The Usual Suspects") {
              kevinSpaceyMovies.map(_.title) should contain("The Usual Suspects")
            }

            it("should contain American Beauty") {
              kevinSpaceyMovies.map(_.title) should contain("American Beauty")
            }
          }

          describe("with director eager-loading") {
            it("should have fetched a director for all movies") {
              forAll(kevinSpaceyMoviesWithDirector) { movie => movie.director shouldBe a[OneFetched[_, _, _]] }
            }

            it("should have fetched Bryan Singer for The Usual Suspects") {
              val usualSuspects = kevinSpaceyMoviesWithDirector.find(_.title == "The Usual Suspects").get

              usualSuspects.director.get.name should be("Bryan Singer")
            }

            it("should have fetched Sam Mendes for American Beauty") {
              val usualSuspects = kevinSpaceyMoviesWithDirector.find(_.title == "American Beauty").get

              usualSuspects.director.get.name should be("Sam Mendes")
            }
          }
        }
      }

      describe("composed relationships") {
        describe("directors for Kevin Spacey") {
          it("should return 2 directors") {
            kevinSpaceyDirectorsCount should be(2)
          }

          it("should contain Bryan Singer") {
            kevinSpaceyDirectors.map(_.name) should contain("Bryan Singer")
          }

          it("should contain Sam Mendes") {
            kevinSpaceyDirectors.map(_.name) should contain("Sam Mendes")
          }
        }

        describe("stars for Bryan Singer") {
          it("should return 1 star") {
            bryanSingerStarsCount should be(1)
          }

          it("should contain Kevin Spacey") {
            bryanSingerStars.map(_.name) should contain("Kevin Spacey")
          }
        }
      }
    }
  }
}
