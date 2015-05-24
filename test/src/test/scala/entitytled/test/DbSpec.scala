package entitytled.test

/* Modified from the strongtyped/active-slick test suite by @rcavalcanti. */

import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import entitytled.Entitytled

trait DbSpec extends BeforeAndAfterAll with Entitytled  {
  self: Suite =>

  import driver.api._

  def setupDatabase(): driver.backend.DatabaseDef

  implicit lazy val db: driver.backend.DatabaseDef = setupDatabase()

  override protected def afterAll(): Unit =
    db.close()

  def query[T](dbAction: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration = 5 seconds): Any =
    runAction(dbAction)

  def commit[T](dbAction: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration = 5 seconds): Any =
    runAction(dbAction.transactionally)

  def rollback[T](dbAction: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration = 5 seconds): Any = {

    case class RollbackException(expected: T) extends RuntimeException("rollback exception")

    val rollbackAction = dbAction.flatMap { result =>
      // NOTE:
      // DBIO.failed returns DBIOAction[Nothing, NoStream, Effect], but we need to preserve T
      // otherwise, we'll end up with a 'R' returned by 'transactionally' method
      // this seems to be need when compiling for 2.10.x (probably a bug fixed on 2.11.x series)
      DBIO.failed(RollbackException(result)).map(_ => result) // map back to T
    }.transactionally.asTry

    val finalAction =
      rollbackAction.map {
        case Success(result)                    => result
        case Failure(RollbackException(result)) => result
        case Failure(other)                     => throw other
      }

    runAction(finalAction)
  }

  private def runAction[T](dbAction: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration): Any =
    Await.result(db.run(dbAction), timeout)
}
