package entitytled

import scala.concurrent.ExecutionContext

import scala.language.implicitConversions

/** Component declaring implicit conversions for relationship value
  * representations.
  */
trait RelationshipRepConversionsComponent {
  self: RelationshipRepComponent with EntityComponent with DriverComponent =>

  import driver.api._

  /** Converts relationship representation's that represent a single value into
    * the represented value.
    *
    * Converts relationship representation's that represent a single value into
    * the represented value, either by using the in-memory value for values
    * that were already fetched, or executing a database query.
    *
    * @param rep The relationship value representation to be converted.
    * @param db  Database definition to be used for executing a query to
    *            retrieve unfetched values.
    * @param ec  Execution context for running a database query.
    *
    * @tparam E The represented entity's type.
    * @tparam I The represented entity's ID type.
    * @tparam T The relationship's target type.
    *
    * @return The value represented by the relationship value representation.
    */
  implicit def oneRepToValue[E <: Entity[E, I], I, T]
  (rep: One[E, I, T])
  (implicit db: Database, ec: ExecutionContext)
  : Option[T] =
    rep.getOrFetchValue

  /** Converts relationship representation's that represent a collection of
    * value into the represented values.
    *
    * Converts relationship representation's that represent a collection of
    * values into the represented values, either by using the in-memory values
    * for values that were already fetched, or executing a database query.
    *
    * @param rep The relationship value representation to be converted.
    * @param db  Database definition to be used for executing a query to
    *            retrieve unfetched values.
    * @param ec  Execution context for running a database query.
    *
    * @tparam E The represented entity's type.
    * @tparam I The represented entity's ID type.
    * @tparam T The relationship's target type.
    *
    * @return The value represented by the relationship value representation.
    */
  implicit def manyRepToValue[E <: Entity[E, I], I, T]
  (rep: Many[E, I, T])
  (implicit db: Database, ec: ExecutionContext)
  : Seq[T] =
    rep.getOrFetchValue
}
