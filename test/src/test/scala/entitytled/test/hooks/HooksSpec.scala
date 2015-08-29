package entitytled.test.hooks

import entitytled.profile.H2Profile
import entitytled.test.DbSpec
import entitytled.test.hooks.model._
import org.scalatest.Suite

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

trait HooksSpec extends DbSpec with H2Profile with PersonComponent with PersonHooksCounterComponent {
  self: Suite =>

  import driver.api._

  val persons = TableQuery[Persons]
  val persistenceHooksCounters = TableQuery[PersonHooksCounters]

  def setupDatabase(): driver.backend.DatabaseDef = {

    val dbUrl = s"jdbc:h2:mem:${this.getClass.getSimpleName}"
    val db = Database.forURL(dbUrl, driver = "org.h2.Driver")
    db.createSession().force()

    val result = db.run((persons.schema ++ persistenceHooksCounters.schema).create)

    Await.result(result, 15 seconds)

    db
  }
}
