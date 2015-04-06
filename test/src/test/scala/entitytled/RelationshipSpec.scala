package entitytled

import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.Inspectors._
import entitytled.holywood._
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class RelationshipSpec extends FunSpec with Matchers {
  Holywood.rollback { implicit session =>
    val stars = TableQuery[Stars]
    val directors = TableQuery[Directors]
    val movies = TableQuery[Movies]

    val spaceyID = (stars returning stars.map(_.id)) += Star(None, "Kevin Spacey", 55)
    val deNiroID = (stars returning stars.map(_.id)) += Star(None, "Robert De Niro", 71)
    val streepID = (stars returning stars.map(_.id)) += Star(None, "Meryl Streep", 65)

    val singer = Director(None, "Bryan Singer")
    val singerID = (directors returning directors.map(_.id)) += singer

    val scorsese = Director(None, "Martin Scorsese")
    val scorseseID = (directors returning directors.map(_.id)) += scorsese

    val usualSuspectsID = (movies returning movies.map(_.id)) +=
      Movie(None, "The Usual Suspects", singerID)
    val goodfellasID = (movies returning movies.map(_.id)) +=
      Movie(None, "Goodfellas", scorseseID)

    TableQuery[MoviesStars] ++= Seq(
      (usualSuspectsID, spaceyID),
      (goodfellasID, deNiroID)
    )

    describe("direct relationships") {
      describe("queryFor method") {
        describe("director for The Usual Suspects") {
          val usualSuspectsDirector = Movie.director.queryFor(usualSuspectsID).firstOption

          it("should return Bryan Singer") {
            usualSuspectsDirector.get.name should be ("Bryan Singer")
          }
        }
      }
    }

    describe("indirect relationships") {
      describe("queryFor method") {
        describe("movies for Kevin Spacey") {
          val kevinSpaceyMoviesQuery = Star.movies.queryFor(spaceyID)
          val kevinSpaceyMovies = kevinSpaceyMoviesQuery.list
          val kevinSpaceyMoviesCount = kevinSpaceyMoviesQuery.length.run

          it("should return 1 movie") {
            kevinSpaceyMoviesCount should be (1)
          }

          it("should contain The Usual Suspects") {
            kevinSpaceyMovies.map(_.title) should contain ("The Usual Suspects")
          }
        }
      }
    }

    describe("'to one' relationships") {
      describe("fetchFor method") {
        describe("director for The Usual Suspects") {
          val usualSuspectsDirector = Movie.director.fetchFor(usualSuspectsID)

          it("should return Bryan Singer") {
            usualSuspectsDirector.get.name should be ("Bryan Singer")
          }
        }
      }
    }

    describe("'to many' relationships") {
      describe("fetchFor method") {
        describe("movies for Kevin Spacey") {
          val kevinSpaceyMovies = Star.movies.fetchFor(spaceyID)

          it("should return 1 movie") {
            kevinSpaceyMovies.length should be (1)
          }

          it("should contain The Usual Suspects") {
            kevinSpaceyMovies.map(_.title) should contain ("The Usual Suspects")
          }
        }
      }
    }

    describe("with eager-loading") {
      describe("Kevin Spacey's movies with directors") {
        val movies = Star.movies.include(Movie.director).fetchFor(spaceyID)

        it("should have fetched a director for all movies") {
          forAll (movies) { movie => movie.director shouldBe a [OneFetched[_, _]] }
        }

        it("should have fetched Bryan Singer for The Usual Suspects") {
          movies.find(_.title == "The Usual Suspects").get.director.getValue.get.name should be ("Bryan Singer")
        }
      }
    }
  }
}
