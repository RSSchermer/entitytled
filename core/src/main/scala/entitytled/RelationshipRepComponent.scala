package entitytled

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

import scala.language.existentials
import scala.language.postfixOps
import scala.language.higherKinds

/** Component grouping declarations regarding relationship value representations
  * for relationship target values belonging to specific entity instances.
  *
  * Needs to be mixed in along with a [[DriverComponent]], an [[EntityComponent]]
  * and a [[RelationshipComponent]].
  */
trait RelationshipRepComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

  /** Represents the value of a relationship for a specific owner instance.
    *
    * @tparam E The owner entity's type.
    * @tparam I The owner entity's ID type.
    * @tparam T The relationship's target type.
    * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
    */
  trait RelationshipRep[E <: Entity[E, I], I, T, C[_]] {

    /** The represented relationship. */
    val relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, C]

    /** The owner entity's ID for persisted entities.
      *
      * Some(ownerId) for persisted owner entities, or None for unpersisted
      * owner entities.
      */
    val ownerId: Option[I]

    /** Describes whether or not the represented value was already fetched into
      * memory.
      */
    val isFetched: Boolean

    /** Returns the represented value if it was already fetched into memory,
      * error otherwise.
      *
      * @throws NoSuchElementException When called on relationship value
      *                                representations for values that have not
      *                                yet been fetched into memory.
      */
    def getValue: C[T]

    /** Will fetch the represented value from persistant storage.
      *
      * @param db          The database definition to be used for executing the
      *                    request.
      * @param ec          The execution context for executing the database the
      *                    request.
      * @param maxDuration The maximum time execution may take before failing.
      */
    def fetchValue(implicit db: Database,
                   ec: ExecutionContext,
                   maxDuration: Duration): C[T]

    /** Will return the represented value from memory for fetched values or
      * fetch it otherwise.
      *
      * @param db          The database definition to be used for executing the
      *                    request.
      * @param ec          The execution context for executing the database the
      *                    request.
      * @param maxDuration The maximum time execution may take before failing.
      */
    def getOrFetchValue(implicit db: Database,
                        ec: ExecutionContext,
                        maxDuration: Duration = 10 seconds): C[T] =
      if (isFetched) getValue else fetchValue
  }

  /** Trait that can be mixed into a [[RelationshipRep]] for representing the
    * value of a relationship that is already fetched into memory.
    *
    * @tparam T The relationships target type.
    * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
    */
  trait Fetched[T, C[_]] {
    self: RelationshipRep[_ <: Entity[_, _], _, T, C] =>

    /** The fetched value. */
    val value: C[T]
    
    val isFetched: Boolean = true

    def getValue: C[T] = value
  }

  /** Trait that can be mixed into a [[RelationshipRep]] for representing the
    * value of a relationship that has not yet been fetched into memory.
    *
    * @tparam T The relationships target type.
    * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
    */
  trait Unfetched[T, C[_]] {
    self: RelationshipRep[_ <: Entity[_, _], _, _, C] =>

    val isFetched: Boolean = false

    def getValue: C[T] = throw new NoSuchElementException("Unfetched.get")
  }

  /** Represents the value of a 'to one' relationship for a specific owner
    * instance.
    */
  sealed trait One[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Option]
  {
    def fetchValue(implicit db: Database,
                   ec: ExecutionContext,
                   maxDuration: Duration = 10 seconds)
    : Option[T] = ownerId match {
      case Some(id) =>
        Await.result(db.run(relationship.actionFor(id)), maxDuration)
      case _ => None
    }
  }

  /** Represents a fetched value of a 'to one' relationship for a specific owner
    * instance.
    */
  case class OneFetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option],
      value: Option[T] = None,
      ownerId: Option[I] = None)
    extends One[E, I, T] with Fetched[T, Option]

  /** Represents an unfetched value of a 'to one' relationship for a specific
    * owner instance.
    */
  case class OneUnfetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option],
      ownerId: Option[I])
    extends One[E, I, T] with Unfetched[T, Option]

  /** Represents the value of a 'to many' relationship for a specific owner
    * instance.
    */
  sealed trait Many[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Seq]
  {
    def fetchValue(implicit db: Database,
                   ec: ExecutionContext,
                   maxDuration: Duration = 10 seconds)
    : Seq[T] = ownerId match {
      case Some(id) =>
        Await.result(db.run(relationship.actionFor(id)), maxDuration)
      case _ => List()
    }
  }

  /** Represents a fetched (in memory) value of a 'to many' relationship for a
    * specific owner instance.
    */
  case class ManyFetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq],
      value: Seq[T] = Seq(),
      ownerId: Option[I] = None)
    extends Many[E, I, T] with Fetched[T, Seq]

  /** Represents a unfetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyUnfetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq],
      ownerId: Option[I])
    extends Many[E, I, T] with Unfetched[T, Seq]
}
