package entitytled.profile

import entitytled.Entitytled
import slick.driver.PostgresDriver

trait PostgresProfile extends Entitytled {
  val driver: PostgresDriver = PostgresDriver
}

object PostgresProfile extends PostgresProfile
