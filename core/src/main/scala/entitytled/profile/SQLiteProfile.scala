package entitytled.profile

import entitytled.Entitytled
import scala.slick.driver.SQLiteDriver

trait SQLiteProfile extends Entitytled {
  val driver: SQLiteDriver = SQLiteDriver
}

object SQLiteProfile extends SQLiteProfile
