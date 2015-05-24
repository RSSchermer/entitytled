package entitytled.profile

import entitytled.Entitytled
import slick.driver.MySQLDriver

trait MySQLProfile extends Entitytled {
  val driver: MySQLDriver = MySQLDriver
}

object MySQLProfile extends MySQLProfile
