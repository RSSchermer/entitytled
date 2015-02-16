package entitytled.profile

import entitytled.Entitytled
import scala.slick.driver.PostgresDriver

trait PostgresProfile extends Entitytled {
  val driver: PostgresDriver = PostgresDriver
}

object PostgresProfile extends PostgresProfile
