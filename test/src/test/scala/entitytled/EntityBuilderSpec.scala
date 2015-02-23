package entitytled

import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.Inspectors._
import entitytled.holywood._
import entitytled.profile.H2Profile._
import entitytled.profile.H2Profile.driver.simple._

class EntityBuilderSpec extends FunSpec with Matchers {
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

    val usualSuspects = (movies returning movies.map(_.id)) +=
      Movie(None, "The Usual Suspects", singerID)
    val goodfellas = (movies returning movies.map(_.id)) +=
      Movie(None, "Goodfellas", scorseseID)

    TableQuery[MoviesStars] ++= Seq(
      (usualSuspects, spaceyID),
      (goodfellas, deNiroID)
    )

    describe("star list") {
      val stars = Star.list

      it("should returns all movie stars") {
        stars should have length 3
        stars.map(_.name) should contain theSameElementsAs Seq("Meryl Streep", "Kevin Spacey", "Robert De Niro")
      }
    }

    describe("stars with movie side-loading") {
      val stars = Star.include(Star.movies).list

      it("should have fetched the movies for each star listed") {
        forAll (stars) { star => star.movies shouldBe a [ManyFetched[_, _]] }
      }

      it("should have fetched The Usual Suspects for Kevin Spacey") {
        stars.find(_.name == "Kevin Spacey").get.movies.get.map(_.title) should contain ("The Usual Suspects")
      }

      it("should not have fetched The Usual Suspects for Robert De Niro") {
        stars.find(_.name == "Robert De Niro").get.movies.get.map(_.title) should not contain "The Usual Suspects"
      }

      describe("with nested director side-loading") {
        val stars = Star.include(Star.movies.include(Movie.director)).list

        it("should have fetched the movies for each star listed") {
          forAll (stars) { star => star.movies shouldBe a [ManyFetched[_, _]] }
        }

        it("should have fetched the directors for each movie") {
          forAll (stars.flatMap(_.movies.get)) { movie => movie.director shouldBe a [OneFetched[_, _]] }
        }

        it("should have fetched Bryan Singer for Kevin Spacey's The Usual Suspects") {
          stars.find(_.name == "Kevin Spacey").get.movies.get.find(_.title == "The Usual Suspects").get
            .director.get.get.name should be ("Bryan Singer")
        }
      }
    }

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

    describe("stars sorted in ascending age") {
      val ascQuery = Star.sortBy(_.age.asc)
      val list = ascQuery.list

      it("should have retrieved Kevin Spacey as the first entry") {
        list.headOption.get.name should be ("Kevin Spacey")
      }

      it("should have retrieved Robert De Niro as the last entry") {
        list.lastOption.get.name should be ("Robert De Niro")
      }

      describe("take 2 stars") {
        val take2Query = ascQuery.take(2)
        val list = take2Query.list

        it("should return only 2 stars") {
          list.length should be (2)
        }

        describe("chained with drop 1") {
          val drop1Query = take2Query.drop(1)
          val list = drop1Query.list

          it("should return only 1 star") {
            list.length should be(1)
          }

          it("should return Meryl Streep") {
            list.headOption.get.name should be ("Meryl Streep")
          }
        }
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

    describe("query for Kevin Spacey by ID") {
      val spaceyQuery = Star.one(spaceyID)
      val spacey = spaceyQuery.get

      it("should have returned Kevin Spacey") {
        spacey.get.name should be ("Kevin Spacey")
      }

      describe("with movie side-loading") {
        val withMovies = spaceyQuery.include(Star.movies)
        val spacey = withMovies.get.get

        it("should have fetched the movies for Kevin Spacey") {
          spacey.movies shouldBe a [ManyFetched[_, _]]
        }

        it("should have fetched The Usual Suspects") {
          spacey.movies.get.map(_.title) should contain ("The Usual Suspects")
        }

        describe("with nested director side-loading") {
          val spaceyWithDirectors = spaceyQuery.include(Star.movies.include(Movie.director)).get.get

          it("should have fetched the movies for Kevin Spacey") {
            spaceyWithDirectors.movies shouldBe a [ManyFetched[_, _]]
          }

          it("should have fetched the directors for each movie") {
            forAll (spaceyWithDirectors.movies.get) { movie => movie.director shouldBe a [OneFetched[_, _]] }
          }

          it("should have fetched Bryan Singer for Kevin Spacey's The Usual Suspects") {
            spaceyWithDirectors.movies.get.find(_.title == "The Usual Suspects").get
              .director.get.get.name should be ("Bryan Singer")
          }
        }
      }
    }
  }
}
