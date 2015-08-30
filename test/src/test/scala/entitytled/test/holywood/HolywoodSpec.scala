package entitytled.test.holywood

import org.scalatest.Suite

import entitytled.test.DbSpec
import entitytled.profile.H2Profile

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import entitytled.test.holywood.model._

trait HolywoodSpec extends DbSpec with H2Profile with DirectorComponent with MovieComponent with StarComponent {
  self: Suite =>

  import driver.api._

  val stars = TableQuery[Stars]
  val directors = TableQuery[Directors]
  val movies = TableQuery[Movies]

  def setupDatabase(): driver.backend.DatabaseDef = {

    val dbUrl = s"jdbc:h2:mem:${this.getClass.getSimpleName};DB_CLOSE_DELAY=-1"
    val db = Database.forURL(dbUrl, driver = "org.h2.Driver")
    db.createSession().force()

    val result = db.run((directors.schema ++ movies.schema ++ stars.schema ++ TableQuery[MoviesStars].schema).create)

    Await.result(result, 15 seconds)

    db
  }
}
