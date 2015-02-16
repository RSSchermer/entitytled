package entitytled.profile

import entitytled.Entitytled
import scala.slick.driver.HsqldbDriver

trait HsqldbProfile extends Entitytled {
  val driver: HsqldbDriver = HsqldbDriver
}

object HsqldbProfile extends HsqldbProfile
