package entitytled.profile

import entitytled.Entitytled
import slick.driver.DerbyDriver

trait DerbyProfile extends Entitytled {
  val driver: DerbyDriver = DerbyDriver
}

object DerbyProfile extends DerbyProfile
