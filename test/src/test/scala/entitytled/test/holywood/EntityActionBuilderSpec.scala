package entitytled.test.holywood

import org.scalatest.Inspectors._
import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class EntityActionBuilderSpec extends FunSpec with HolywoodSpec with Matchers {

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

      stars <- Star.all.result
      starsWithMovies  <- Star.all.include(Star.movies).result
      starsWithMoviesWithDirectors <- Star.all.include(Star.movies.include(Movie.director)).result
      moviesWithDirectorAndStars <- Movie.all.include(Movie.director, Movie.stars).result
      starsOlderThan65 <- Star.all.filter(_.age >= 65).result
      starsBetween65And70 <- Star.all.filter(_.age >= 65).filter(_.age <= 70).result
      starsByAgeAsc <- Star.all.sortBy(_.age.asc).result
      starsByAgeAscTake2 <- Star.all.sortBy(_.age.asc).take(2).result
      starsByAgeAscTake2Drop1 <- Star.all.sortBy(_.age.asc).take(2).drop(1).result
      starsByAgeDesc <- Star.all.sortBy(_.age.desc).result
      spacey <- Star.one(spaceyID).result
      spaceyWithMovies <- Star.one(spaceyID).include(Star.movies).result
      spaceyWithMoviesWithDirector <- Star.one(spaceyID).include(Star.movies.include(Movie.director)).result
      spaceyWithDirectors <- Star.one(spaceyID).include(Star.directors).result
      suspects <- Movie.one(usualSuspectsID).result
      suspectsWithDirectorAndStars <- Movie.one(usualSuspectsID).include(Movie.director, Movie.stars).result
    } yield {

      describe("star list") {
        it("should returns all movie stars") {
          stars.map(_.name) should contain theSameElementsAs Seq(
            "Meryl Streep",
            "Kevin Spacey",
            "Robert De Niro"
          )
        }
      }

      describe("stars with movie eager-loading") {
        it("should have fetched the movies for each star listed") {
          forAll(starsWithMovies) { star => star.movies shouldBe a[ManyFetched[_, _, _]] }
        }

        it("should have fetched The Usual Suspects for Kevin Spacey") {
          val spaceyMovies = starsWithMovies.find(_.name == "Kevin Spacey").map(_.movies).toList.flatten.map(_.title)

          spaceyMovies should contain("The Usual Suspects")
        }

        it("should not have fetched The Usual Suspects for Robert De Niro") {
          val deNiroMovies = starsWithMovies.find(_.name == "Robert De Niro").map(_.movies).toList.flatten.map(_.title)

          deNiroMovies should not contain "The Usual Suspects"
        }
      }

      describe("stars with movie eager-loading with nested director eager-loading") {
        it("should have fetched the movies for each star listed") {
          forAll(starsWithMoviesWithDirectors) { star => star.movies shouldBe a[ManyFetched[_, _, _]] }
        }

        it("should have fetched the directors for each movie") {
          forAll(starsWithMoviesWithDirectors.flatMap(_.movies)) { movie =>
            movie.director shouldBe a[OneFetched[_, _, _]]
          }
        }

        it("should have fetched Bryan Singer for Kevin Spacey's The Usual Suspects") {
          val directorName = for {
            spacey <- starsWithMoviesWithDirectors.find(_.name == "Kevin Spacey")
            usualSuspects <- spacey.movies.find(_.title == "The Usual Suspects")
            director <- usualSuspects.director
          } yield director.name

          directorName should be(Some("Bryan Singer"))
        }
      }

      describe("movies with director and star eager-loading") {
        it("should have fetched the stars for each movie listed") {
          forAll(moviesWithDirectorAndStars) { movie => movie.stars shouldBe a[ManyFetched[_, _, _]] }
        }

        it("should have fetched the director for each movie listed") {
          forAll(moviesWithDirectorAndStars) { movie => movie.director shouldBe a[OneFetched[_, _, _]] }
        }

        val usualSuspects = moviesWithDirectorAndStars.find(_.title == "The Usual Suspects")

        it("should have fetched Kevin Spacey for The Usual Suspects") {
          usualSuspects.map(_.stars).toList.flatten.map(_.name) should contain("Kevin Spacey")
        }

        it("should have fetched Brian Singer for The Usual Suspects") {
          usualSuspects.flatMap(_.director).map(_.name) should be(Some("Bryan Singer"))
        }
      }

      describe("the resulting list after filtering for only 65 and older") {
        it("should not contain Kevin Spacey") {
          starsOlderThan65.map(_.name) should not contain "Kevin Spacey"
        }

        it("should contain Streep and De Niro") {
          starsOlderThan65.map(_.name) should contain allOf("Meryl Streep", "Robert De Niro")
        }
      }

      describe("the resulting list after chaining an additional filter for 70 and younger") {
        it("should not contain Robert De Niro") {
          starsBetween65And70.map(_.name) should not contain "Robert De Niro"
        }

        it("should contain Meryl Streep") {
          starsBetween65And70.map(_.name) should contain("Meryl Streep")
        }
      }

      describe("stars sorted by ascending age") {
        it("should have retrieved Kevin Spacey as the first entry") {
          starsByAgeAsc.headOption.map(_.name) should be(Some("Kevin Spacey"))
        }

        it("should have retrieved Robert De Niro as the last entry") {
          starsByAgeAsc.lastOption.map(_.name) should be(Some("Robert De Niro"))
        }
      }

      describe("take 2 stars with stars sorted by ascending age") {
        it("should return only 2 stars") {
          starsByAgeAscTake2.length should be(2)
        }
      }

      describe("take 2 stars, drop 1, with stars sorted by ascending age") {
        it("should return only 1 star") {
          starsByAgeAscTake2Drop1.length should be(1)
        }

        it("should return Meryl Streep") {
          starsByAgeAscTake2Drop1.headOption.map(_.name) should be(Some("Meryl Streep"))
        }
      }

      describe("stars sorted in descending age") {
        it("should have retrieved Robert De Niro as the first entry") {
          starsByAgeDesc.headOption.map(_.name) should be(Some("Robert De Niro"))
        }

        it("should have retrieved Kevin Spacey as the last entry") {
          starsByAgeDesc.lastOption.map(_.name) should be(Some("Kevin Spacey"))
        }
      }

      describe("query for Kevin Spacey by ID") {
        it("should have returned Kevin Spacey") {
          spacey.map(_.name) should be(Some("Kevin Spacey"))
        }
      }

      describe("query for Kevin Spacey with movie eager-loading") {
        it("should have fetched the movies for Kevin Spacey") {
          spaceyWithMovies.map(_.movies).get shouldBe a[ManyFetched[_, _, _]]
        }

        it("should have fetched The Usual Suspects") {
          spaceyWithMovies.map(_.movies).toList.flatten.map(_.title) should contain("The Usual Suspects")
        }
      }

      describe("query for Kevin Spacey with movie eager-loading with nested director eager-loading") {
        it("should have fetched the movies for Kevin Spacey") {
          spaceyWithMoviesWithDirector.map(_.movies).get shouldBe a[ManyFetched[_, _, _]]
        }

        it("should have fetched the directors for each movie") {
          forAll(spaceyWithMoviesWithDirector.map(_.movies).toList.flatten.map(_.director)) { director =>
            director shouldBe a[OneFetched[_, _, _]]
          }
        }

        it("should have fetched Bryan Singer for Kevin Spacey's The Usual Suspects") {
          val directorName = for {
            spacey <- spaceyWithMoviesWithDirector
            suspects <- spacey.movies.find(_.title == "The Usual Suspects")
            director <- suspects.director
          } yield director.name

          directorName should be(Some("Bryan Singer"))
        }
      }

      describe("Kevin Spacey with director (composed relationship) eager-loading") {
        it("should have fetched the directors for Kevin Spacey") {
          spaceyWithDirectors.map(_.directors).get shouldBe a[ManyFetched[_, _, _]]
        }

        it("should have fetched 1 director") {
          spaceyWithDirectors.map(_.directors.length) should be(Some(1))
        }

        it("should have fetched Bryan Singer") {
          spaceyWithDirectors.map(_.directors).toList.flatten.map(_.name) should contain("Bryan Singer")
        }
      }

      describe("query for The Usual Suspects by ID") {
        it("should have returned The Usual suspects") {
          suspects.map(_.title) should be(Some("The Usual Suspects"))
        }
      }

      describe("The Usual Suspects with director and star eager-loading") {
        it("should have fetched the stars for each movie listed") {
          suspectsWithDirectorAndStars.map(_.stars).get shouldBe a[ManyFetched[_, _, _]]
        }

        it("should have fetched the director for each movie listed") {
          suspectsWithDirectorAndStars.map(_.director).get shouldBe a[OneFetched[_, _, _]]
        }

        it("should have fetched Kevin Spacey for The Usual Suspects") {
          suspectsWithDirectorAndStars.map(_.stars).toList.flatten.map(_.name) should contain("Kevin Spacey")
        }

        it("should have fetched Brian Singer for The Usual Suspects") {
          suspectsWithDirectorAndStars.flatMap(_.director).map(_.name) should be(Some("Bryan Singer"))
        }
      }
    }
  }
}
