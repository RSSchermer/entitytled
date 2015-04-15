package entitytled

import scala.language.implicitConversions

trait RelationshipRepConversionsComponent {
  self: RelationshipRepComponent with EntityComponent with DriverComponent =>

  import driver.simple._

  implicit def oneRepToValue[E <: Entity[E, I], I, T](rep: One[E, I, T])(implicit s: Session): Option[T] =
    rep.getOrFetchValue

  implicit def manyRepToValue[E <: Entity[E, I], I, T](rep: Many[E, I, T])(implicit s: Session): Seq[T] =
    rep.getOrFetchValue
}
