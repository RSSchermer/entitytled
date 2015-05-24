package entitytled

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import scala.language.existentials
import scala.language.postfixOps
import scala.language.higherKinds

trait RelationshipRepComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

  /** Represents the value of a relationship for a specific owner instance. */
  trait RelationshipRep[Owner <: Entity[Owner, I], I, T, C[_]] {

    /** The represented relationship. */
    val relationship: Relationship[_ <: EntityTable[Owner, I], _ <: Table[T], Owner, I, T, C]

    /** The ID of the owner entity. */
    val ownerId: Option[I]

    /** Describes whether or not the represented value was already fetched into
      * memory. */
    val isFetched: Boolean

    /** Returns the represented value if it was already fetched into memory,
      * error otherwise. */
    def getValue: C[T]

    /** Will fetch the represented value from persistant storage. */
    def fetchValue(implicit db: Database, ec: ExecutionContext, maxDuration: Duration): C[T]

    /** Will return the represented value from memory or fetch it otherwise. */
    def getOrFetchValue(implicit db: Database, ec: ExecutionContext, maxDuration: Duration = 10 seconds): C[T] =
      if (isFetched) getValue else fetchValue
  }

  /** Represents a value of a relationship that is already fetched from storage. */
  trait Fetched[T, C[_]] {
    self: RelationshipRep[_ <: Entity[_, _], _, T, C] =>
    
    val value: C[T]
    
    val isFetched: Boolean = true

    def getValue: C[T] = value
  }

  /** Represents a value of a relationship that is not yet fetched from storage. */
  trait Unfetched[T, C[_]] {
    self: RelationshipRep[_ <: Entity[_, _], _, _, C] =>

    val isFetched: Boolean = false

    def getValue: C[T] = throw new NoSuchElementException("Unfetched.get")
  }

  /** Represents the value of a 'to one' relationship for a specific owner
    * instance. */
  sealed abstract class One[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Option]
  {
    def fetchValue(implicit db: Database, ec: ExecutionContext, maxDuration: Duration = 10 seconds)
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
  sealed abstract class Many[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Seq]
  {
    def fetchValue(implicit db: Database, ec: ExecutionContext, maxDuration: Duration = 10 seconds)
    : Seq[T] = ownerId match {
      case Some(id) =>
        Await.result(db.run(relationship.actionFor(id)), maxDuration)
      case _ => List()
    }
  }

  /** Represents a fetched value of a 'to many' relationship for a specific
    * owner instance.
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
