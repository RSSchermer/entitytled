package entitytled

trait RelationshipRepConversionsComponent {
  self: RelationshipRepComponent with EntityComponent with DriverComponent =>

  import driver.simple._

  implicit def oneRepToValue[E <: Entity[E], T](rep: One[E, T])(implicit s: Session): Option[T] =
    rep.getOrFetchValue

  implicit def manyRepToValue[E <: Entity[E], T](rep: Many[E, T])(implicit s: Session): Seq[T] =
    rep.getOrFetchValue
}
