package entitytled

import language.existentials

trait RelationshipRepComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.simple._

  /** Represents the value of a relationship for a specific owner instance. */
  trait RelationshipRep[Owner <: Entity[Owner], Value] {

    /** The ID of the owner entity. */
    val ownerId: Option[Owner#IdType]

    /** Decribes whether or not the represented value was already fetched into
      * memory. */
    val isFetched: Boolean

    /** Returns the represented value if it was already fetched into memory,
      * error otherwise. */
    def get: Value

    /** Will fetch the represented value from persistant storage. */
    def fetch(implicit session: Session): Value

    /** Will return the represented value from memory or fetch it otherwise. */
    def getOrFetch(implicit session: Session): Value =
      if (isFetched) get else fetch
  }

  /** Represents the value of a 'to one' relationship for a specific owner
    * instance.
    */
  sealed abstract class One[E <: Entity[E], T]
    extends RelationshipRep[E, Option[T]]
  {
    val relationship: ToOneRelationship[_ <: EntityTable[E], _ <: Table[T], E, T]

    def fetch(implicit session: Session): Option[T] = ownerId match {
      case Some(id) => relationship.fetchFor(id)
      case _ => None
    }
  }

  /** Represents a fetched value of a 'to one' relationship for a specific owner
    * instance.
    */
  case class OneFetched[E <: Entity[E], T](
      override val relationship: ToOneRelationship[_ <: EntityTable[E], _ <: Table[T], E, T],
      value: Option[T] = None,
      override val ownerId: Option[E#IdType] = None)
    extends One[E, T]
  {
    val isFetched: Boolean = true

    def get: Option[T] = value
  }

  /** Represents an unfetched value of a 'to one' relationship for a specific
    * owner instance.
    */
  case class OneUnfetched[E <: Entity[E], T](
      override val relationship: ToOneRelationship[_ <: EntityTable[E], _ <: Table[T], E, T],
      override val ownerId: Option[E#IdType])
    extends One[E, T]
  {
    val isFetched: Boolean = false

    def get: Option[T] = throw new NoSuchElementException("OneUnfetched.get")
  }

  /** Represents the value of a 'to many' relationship for a specific owner
    * instance.
    */
  sealed abstract class Many[E <: Entity[E], T]
    extends RelationshipRep[E, Seq[T]]
  {
    val relationship: ToManyRelationship[_ <: EntityTable[E], _ <: Table[T], E, T]

    def fetch(implicit session: Session): Seq[T] = ownerId match {
      case Some(id) => relationship.fetchFor(id)
      case _ => List()
    }
  }

  /** Represents a fetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyFetched[E <: Entity[E], T](
      override val relationship: ToManyRelationship[_ <: EntityTable[E], _ <: Table[T], E, T],
      values: Seq[T] = Seq(),
      override val ownerId: Option[E#IdType] = None)
    extends Many[E, T]
  {
    val isFetched: Boolean = true

    def get: Seq[T] = values
  }

  /** Represents a unfetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyUnfetched[E <: Entity[E], T](
      override val relationship: ToManyRelationship[_ <: EntityTable[E], _ <: Table[T], E, T],
      override val ownerId: Option[E#IdType])
    extends Many[E, T]
  {
    val isFetched: Boolean = false

    def get: Seq[T] = throw new NoSuchElementException("ManyUnfetched.get")
  }
}
