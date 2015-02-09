package entitytled

import scala.slick.driver.JdbcProfile

trait DriverComponent {
  val driver: JdbcProfile
}
