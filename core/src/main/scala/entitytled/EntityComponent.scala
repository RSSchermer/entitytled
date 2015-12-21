package entitytled

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.language.existentials

import annotation.implicitNotFound

import scala.reflect.macros._

/** Component grouping together some entity relation declarations.
  *
  * Needs to be mixed in along with a [[DriverComponent]],
  * [[RelationshipComponent]], and a [[RelationshipRepComponent]].
  */
trait EntityComponent {
  self: DriverComponent
    with RelationshipComponent
    with RelationshipRepComponent
  =>
  
  import driver.api._

  /** Base class for entities.
    *
    * Extend this class with a case class to define an entity. Entities need to
    * be uniquely identifiable by an ID. Clients need to implement the `id`
    * member
    *
    * @param includes       The included (eager-loaded) relationships.
    * @param includesSetter Helper needed for updating the included
    *                       relationship values.
    * @tparam E The extending entity type itself (a self bound).
    * @tparam I The ID type.
    */
  abstract class Entity[E <: Entity[E, I], I](
    implicit
      includes: Includes[E] = Includes(),
      includesSetter: IncludesSetter[E]
  ) {

    /** The entity's unique ID.
      *
      * An entity needs to be uniquely identifiable by this ID.
      */
    val id: Option[I]

    /** Returns a new instance with the given set if includes (eager-loaded
      * relationship values).
      *
      * @param includes The set of relationship values that are to be included
      *                 on the new entity instance.
      */
    def withIncludes(includes: Includes[E]): E =
      includesSetter.withIncludes(this.asInstanceOf[E], includes)

    /** Returns a new instance onto which the given value is included
      * (eager-loaded) for the given relationship.
      *
      * @param relationship The relationship the value is associated with.
      * @param value        The value to include (eager-load) on the new
      *                     instance.
      * @tparam T The relationships target type.
      * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
      */
    def setInclude[T, C[_]](
        relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, C],
        value: C[T]
    ): E =
      withIncludes(includes.updated(relationship, value))

    /** Returns a new instance onto which the given relationship value
      * representation is included (eager-loaded)
      *
      * @param relationshipRep The relationship value representation to be
      *                        included (eager-loaded) onto the new instance.
      * @tparam T The relationships target type.
      * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
      */
    def setInclude[T, C[_]](
        relationshipRep: RelationshipRep[E, I, T, C] with Fetched[T, C]
    ): E =
      setInclude(relationshipRep.relationship, relationshipRep.value)

    /** Returns a relationship value representation for the given 'to one'
      * relationship.
      *
      * @param relationship The 'to one' relationship the represented value
      *                     belongs to.
      * @tparam T The relationship target's type.
      */
    def one[T](
        relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option]
    ): One[E, I, T] =
      includes.get(relationship) match {
        case Some(value) =>
          OneFetched[E, I, T](relationship, value, id)
        case _ =>
          OneUnfetched[E, I, T](relationship, id)
      }

    /** Returns a relationship value representation for the given 'to many'
      * relationship.
      *
      * @param relationship The 'to many' relationship the represented value
      *                     belongs to.
      * @tparam T The relationship target's type.
      */
    def many[T](
        relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq]
    ): Many[E, I, T] =
      includes.get(relationship) match {
        case Some(value) =>
          ManyFetched[E, I, T](relationship, value, id)
        case _ =>
          ManyUnfetched[E, I, T](relationship, id)
      }
  }

  /** Base class for entity tables.
    *
    * Extend this class to define a table for an entity type. Entity tables must
    * define an ID column. Clients need to implement the `id` member.
    *
    * @tparam E The entity type this table represents.
    * @tparam I The entity's ID type.
    */
  abstract class EntityTable[E <: Entity[E, I], I : BaseColumnType](
      tag: Tag,
      schemaName: Option[String],
      tableName: String)
    extends Table[E](tag, schemaName, tableName)
  {
    def this(tag: Tag, tableName: String) =
      this(tag, None, tableName)

    /** The entity table's ID column. */
    def id: Rep[I]
  }

  /** Type alias for a relationship-value pair.
    *
    * @tparam E The owner entity's type.
    */
  type Include[E <: Entity[E, _]] =
    (Relationship[_ <: EntityTable[E, _], _ <: Table[_], E, _, T, C], C[T]) forSome { type C[_]; type T }

  /** Set of relationship-value pairs that can be included onto an entity
    * instance.
    *
    * Should be instantiated through its companion object.
    *
    * @param values The relationship-value pairs.
    * @tparam E The ower entity's type.
    */
  class Includes[E <: Entity[E, _]](values: Seq[Include[E]]) {
    private val relationshipMap: Map[Any, Any] = values.toMap

    /** Returns the value associated with the given relationship if the
      * relationship is included.
      *
      * Returns Some(relationshipValue) if the relationship is included, or
      * None otherwise.
      *
      * @param relationship The relationship to look up.
      * @tparam T The relationships target type.
      * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
      */
    def get[T, C[_]](
        relationship: Relationship[_ <: EntityTable[E, _], _ <: Table[_], E, _, T, C]
    ): Option[C[T]] =
      relationshipMap.get(relationship).asInstanceOf[Option[C[T]]]

    /** Returns a new set of includes, in which the given relationship's
      * included value is updated to the given value.
      *
      * Includes the given relationship with the given value if the
      * relationship was not already included. Changes the given relationship's
      * value to the new value if the relationship was already included.
      *
      * @param relationship The relationship of which the value is to be
      *                     updated.
      * @param value        The updated relationship value.
      * @tparam T The relationships target type.
      * @tparam C Value container type ([[scala.Option]], [[scala.Seq]], ...)
      */
    def updated[T, C[_]](
        relationship: Relationship[_ <: EntityTable[E, _], _ <: Table[_], E, _, T, C],
        value: C[T]
    ): Includes[E] = {
      val map = relationshipMap.updated(relationship, value)

      new Includes(map.toSeq.asInstanceOf[Seq[Include[E]]])
    }
  }

  object Includes {

    /** Creates a new includes set holding the given relationship-value pairs.
      *
      * @param values The relationship-value pairs.
      * @tparam E The owner entity's type.
      */
    def apply[E <: Entity[E, _]](values: Include[E]*): Includes[E] =
      new Includes(values)
  }

  /** Can set a new set of included relationship values on an entity instance.
    *
    * Intended to be instantiated through implicit materialization by it's
    * companion object.
    *
    * @tparam E The type of the entity that owns the includes.
    */
  @implicitNotFound("Could not create an includes setter for entity type " +
    "{E}. Did you make sure {E} is a case class?")
  trait IncludesSetter[E <: Entity[E, _]] {
    def withIncludes(instance: E, includes: Includes[E]): E
  }

  object IncludesSetter {

    /** Materializes a includes setter for entity type E.
      *
      * @tparam E The type of the entity that owns the includes.
      */
    implicit def materializeIncludesSetter[
        E <: Entity[E, _]
    ]: IncludesSetter[E] =
      macro MaterializeIncludesSetterImpl.apply[E]
  }
}

object MaterializeIncludesSetterImpl {
  def apply[E : c.WeakTypeTag](c: Context) = {
    import c.universe._

    val tpe = c.weakTypeOf[E].typeSymbol.asClass

    c.Expr(q"""
    new IncludesSetter[$tpe] {
      def withIncludes(instance: $tpe, includes: Includes[$tpe]): $tpe = {
        implicit val i = includes
        instance.copy()
      }
    }""")
  }
}
