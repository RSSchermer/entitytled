package entitytled

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

import scala.language.existentials
import scala.language.postfixOps
import scala.language.higherKinds
import scala.language.implicitConversions

/** Component grouping declarations regarding relationship value representations
  * for relationship target values belonging to specific entity instances.
  *
  * Needs to be mixed in along with a [[DriverComponent]], an
  * [[EntityComponent]] and a [[RelationshipComponent]].
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

    /** Database action that will result in the represented value.
      *
      * Will execute a query for unfetched values, or simply wrap the value
      * in a database I/O action for fetched values (without executing an
      * additional value).
      */
    def valueAction(
      implicit
        ec: ExecutionContext
    ): DBIOAction[C[T], NoStream, Effect.Read]

    /** Treats this represented value as unfetched.
      *
      * Treats this represented value as unfetched, which means the value action
      * will execute a query, regardless of whether the value was prefetched.
      */
    def asUnfetched: RelationshipRep[E, I, T, C] with Unfetched[T, C]
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

    def valueAction(
      implicit ec: ExecutionContext
    ): DBIOAction[C[T], NoStream, Effect.Read] =
      DBIO.successful(value)
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
  }

  /** Represents the value of a 'to one' relationship for a specific owner
    * instance.
    */
  sealed trait One[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Option]

  /** Represents a fetched value of a 'to one' relationship for a specific owner
    * instance.
    */
  case class OneFetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option],
      value: Option[T] = None,
      ownerId: Option[I] = None
  ) extends One[E, I, T] with Fetched[T, Option] {

    def asUnfetched: OneUnfetched[E, I, T] =
      OneUnfetched(relationship, ownerId)
  }

  /** Represents an unfetched value of a 'to one' relationship for a specific
    * owner instance.
    */
  case class OneUnfetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option],
      ownerId: Option[I]
  ) extends One[E, I, T] with Unfetched[T, Option] {

    def valueAction(
      implicit
        ec: ExecutionContext
    ): DBIOAction[Option[T], NoStream, Effect.Read] =
      ownerId match {
        case Some(id) =>
          relationship.actionFor(id)
        case _ =>
          DBIO.successful(None)
      }

    def asUnfetched: OneUnfetched[E, I, T] = this
  }

  /** Represents the value of a 'to many' relationship for a specific owner
    * instance.
    */
  sealed trait Many[E <: Entity[E, I], I, T]
    extends RelationshipRep[E, I, T, Seq]

  /** Represents a fetched (in memory) value of a 'to many' relationship for a
    * specific owner instance.
    */
  case class ManyFetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq],
      value: Seq[T] = Seq(),
      ownerId: Option[I] = None
  ) extends Many[E, I, T] with Fetched[T, Seq] {

    def asUnfetched: ManyUnfetched[E, I, T] =
      ManyUnfetched(relationship, ownerId)
  }

  /** Represents a unfetched value of a 'to many' relationship for a specific
    * owner instance.
    */
  case class ManyUnfetched[E <: Entity[E, I], I, T](
      relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq],
      ownerId: Option[I]
  ) extends Many[E, I, T] with Unfetched[T, Seq] {

    def valueAction(
      implicit
        ec: ExecutionContext
    ): DBIOAction[Seq[T], NoStream, Effect.Read] =
      ownerId match {
        case Some(id) =>
          relationship.actionFor(id)
        case _ =>
          DBIO.successful(Seq())
      }

    def asUnfetched: ManyUnfetched[E, I, T] = this
  }
}

/** Component declaring implicit conversions for relationship value
  * representations.
  */
trait RelationshipRepConversionsComponent {
  self: RelationshipRepComponent with EntityComponent with DriverComponent =>

  import driver.api._

  /** Converts relationship value representation into the related value it is
    * representing.
    *
    * Converts relationship value representation into the related value it is
    * representing, either by using the in-memory values for values that were
    * already fetched, or executing a database query.
    *
    * @param rep The relationship value representation to be converted.
    * @param db  Database definition to be used for executing a query to
    *            retrieve unfetched values.
    * @param ec  Execution context for running a database query.
    *
    * @tparam E The represented entity's type.
    * @tparam I The represented entity's ID type.
    * @tparam T The relationship's target type.
    * @tparam C The value container type.
    *
    * @return The value represented by the relationship value representation.
    */
  implicit def relationshipRepToValue[E <: Entity[E, I], I, T, C[_]](
      rep: RelationshipRep[E, I, T, C]
  )(implicit
      db: Database,
      ec: ExecutionContext
  ): C[T] =
    Await.result(db.run(rep.valueAction), 5 seconds)
}
