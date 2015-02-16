package entitytled.profile

import entitytled.Entitytled
import scala.slick.driver.MySQLDriver

trait MySQLProfile extends Entitytled {
  val driver: MySQLDriver = MySQLDriver
}

object MySQLProfile extends MySQLProfile
