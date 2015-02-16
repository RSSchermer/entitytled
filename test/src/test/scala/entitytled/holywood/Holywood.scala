package entitytled.holywood

import entitytled.profile.H2Profile.driver.simple._

sealed trait TxOps {
  def complete(sess: Session): Unit
}

object Rollback extends TxOps {
  override def complete(sess: Session): Unit = sess.rollback()
}

object Commit extends TxOps {
  override def complete(sess: Session): Unit = ()
}

object Holywood {

  lazy val db = {
    val db = Database.forURL("jdbc:h2:mem:active-slick", driver = "org.h2.Driver")
    val keepAliveSession = db.createSession()
    keepAliveSession.force() // keep the database in memory with an extra connection
    db.withTransaction { implicit session =>
      (
        TableQuery[Directors].ddl ++
        TableQuery[Stars].ddl ++
        TableQuery[Movies].ddl ++
        TableQuery[MoviesStars].ddl
      ).create
    }
    db
  }

  def commit[T](block: Session => T): T = apply(Commit)(block)
  def rollback[T](block: Session => T): T = apply(Rollback)(block)

  private def apply[T](block: Session => T): T = apply(Rollback)(block)

  def autoCommit[T](block: Session => T): T = {
    // slick sessions are autocommit
    db.withSession { implicit session =>
      block(session)
    }
  }

  def apply[T](txOps: TxOps)(block: Session => T): T = {
    db.withTransaction { implicit session =>
      val result = block(session)
      txOps.complete(session)
      result
    }
  }
}
