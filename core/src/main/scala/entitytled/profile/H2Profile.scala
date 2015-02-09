package entitytled.profile

import entitytled.Entitytled
import scala.slick.driver.H2Driver

trait H2Profile extends Entitytled {
  val driver: H2Driver = H2Driver
}

object H2Profile extends H2Profile
