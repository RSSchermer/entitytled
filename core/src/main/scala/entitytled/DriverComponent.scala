package entitytled

import slick.driver.JdbcProfile

trait DriverComponent {
  val driver: JdbcProfile
}
