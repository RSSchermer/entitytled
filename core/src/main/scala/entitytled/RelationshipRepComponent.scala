package entitytled

import language.existentials

trait RelationshipRepComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.simple._

  /** Represents the value of a relationship for a specific owner instance. */
  trait RelationshipRep[Owner <: Entity[Owner], T, Value] {

    /** The represented relationship. */
    val relationship: Relationship[_ <: EntityTable[Owner], _ <: Table[T], Owner, T, Value]

    /** The ID of the owner entity. */
    val ownerId: Option[Owner#IdType]

    /** Describes whether or not the represented value was already fetched into
      * memory. */
    val isFetched: Boolean

    /** Returns the represented value if it was already fetched into memory,
      * error otherwise. */
    def getValue: Value

    /** Will fetch the represented value from persistant storage. */
    def fetchValue(implicit session: Session): Value

    /** Will return the represented value from memory or fetch it otherwise. */
    def getOrFetchValue(implicit session: Session): Value =
      if (isFetched) getValue else fetchValue
  }

  /** Represents a value of a relationship that is already fetched from storage. */
  trait Fetched[Value] {
    self: RelationshipRep[_ <: Entity[_], _, Value] =>
    
    val value: Value
    
    val isFetched: Boolean = true

    def getValue: Value = value
  }

  /** Represents a value of a relationship that is not yet fetched from storage. */
  trait Unfetched[Value] {
    self: RelationshipRep[_ <: Entity[_], _, Value] =>

    val isFetched: Boolean = false

    def getValue: Value = throw new NoSuchElementException("Unfetched.get")
  }

  /** Represents the value of a 'to one' relationship for a specific owner
    * instance. */
  sealed abstract class One[E <: Entity[E], T]
    extends RelationshipRep[E, T, Option[T]]
  {
    def fetchValue(implicit session: Session): Option[T] = ownerId match {
      case Some(id) => relationship.fetchFor(id)
      case _ => None
    }
  }

  /** Represents a fetched value of a 'to one' relationship for a specific owner
    * instance.
    */
  case class OneFetched[E <: Entity[E], T](
      relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Option[T]],
      value: Option[T] = None,
      ownerId: Option[E#IdType] = None)
    extends One[E, T] with Fetched[Option[T]]

  /** Represents an unfetched value of a 'to one' relationship for a specific
    * owner instance.
    */
  case class OneUnfetched[E <: Entity[E], T](
      relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Option[T]],
      ownerId: Option[E#IdType])
    extends One[E, T] with Unfetched[Option[T]]

  /** Represents the value of a 'to many' relationship for a specific owner
    * instance.
    */
  sealed abstract class Many[E <: Entity[E], T]
    extends RelationshipRep[E, T, Seq[T]]
  {
    def fetchValue(implicit session: Session): Seq[T] = ownerId match {
      case Some(id) => relationship.fetchFor(id)
      case _ => List()
    }
  }

  /** Represents a fetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyFetched[E <: Entity[E], T](
      relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Seq[T]],
      value: Seq[T] = Seq(),
      ownerId: Option[E#IdType] = None)
    extends Many[E, T] with Fetched[Seq[T]]

  /** Represents a unfetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyUnfetched[E <: Entity[E], T](
      relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Seq[T]],
      ownerId: Option[E#IdType])
    extends Many[E, T] with Unfetched[Seq[T]]
}
