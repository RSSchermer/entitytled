package entitytled

import scala.concurrent.ExecutionContext

import scala.language.implicitConversions

trait RelationshipRepConversionsComponent {
  self: RelationshipRepComponent with EntityComponent with DriverComponent =>

  import driver.api._

  implicit def oneRepToValue[E <: Entity[E, I], I, T](rep: One[E, I, T])(implicit db: Database, ec: ExecutionContext): Option[T] =
    rep.getOrFetchValue

  implicit def manyRepToValue[E <: Entity[E, I], I, T](rep: Many[E, I, T])(implicit db: Database, ec: ExecutionContext): Seq[T] =
    rep.getOrFetchValue
}
