package entitytled.profile

import entitytled.Entitytled
import slick.driver.HsqldbDriver

trait HsqldbProfile extends Entitytled {
  val driver: HsqldbDriver = HsqldbDriver
}

object HsqldbProfile extends HsqldbProfile
