package entitytled

import scala.language.higherKinds

/** Component for adding relationship composition.
  *
  * Needs to be mixed in along with a [[DriverComponent]], an [[EntityComponent]]
  * and a [[RelationshipComponent]].
  */
trait RelationshipCompositionComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

  /** Composes a base relationship from an owner entity to a joining entity,
    * with an attached relationship from the joining entity to a target relation,
    * into a relationship from the owner entity to the target relation.
    *
    * @tparam Owner The owner entity's table type.
    * @tparam Join  The joining entity's table type
    * @tparam To    The target relation's table type.
    * @tparam O     The owner entity's type.
    * @tparam J     The joining entity's type.
    * @tparam T     The target relation's type.
    * @tparam I1    The owner entity's ID type.
    * @tparam C     The type of the functor containing target relation values
    *               that are retrieved from the database.
    * @tparam C1    The type of the functor containing retrieved values for the
    *               base relationship.
    * @tparam C2    The type of the functor containing retrieved values for the
    *               attached relationship.
    */
  trait ComposedRelationship[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1, C[_], C1[_], C2[_]]
    extends Relationship[Owner, To, O, I1, T, C]
  {
    val baseRelationship: Relationship[Owner, Join, O, I1, J, C1]

    val attachedRelationship: Relationship[Join, To, J, _, T, C2]

    def queryFor(id: I1): Query[To, T, Seq] =
      attachedRelationship.queryFor(baseRelationship.queryFor(id))

    def queryFor(query: Query[Owner, O, Seq]): Query[To, T, Seq] =
      attachedRelationship.queryFor(baseRelationship.queryFor(query))

    def innerJoinFor(query: Query[Owner, O, Seq]): Query[(Owner, To), (O, T), Seq] =
      attachedRelationship.expandJoin(baseRelationship.innerJoinFor(query))

    def expandJoin[W <: Table[E], E](from: Query[(W, Owner), (E, O), Seq]): Query[(W, To), (E, T), Seq] =
      attachedRelationship.expandJoin(baseRelationship.expandJoin(from))
  }

  /** A 'to many' relationship composed with another relationship, which acts as
    * a 'to many' relationship.
    */
  class ToManyComposed[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1, C[_]](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, Seq],
      val attachedRelationship: Relationship[Join, To, J, _, T, C])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Seq, Seq, C]
    with ToManyRelationship[Owner, To, O, I1, T]

  /** A relationship composed with a 'to many' relationship, which acts as a
    * 'to many' relationship.
    */
  class ComposedToMany[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1, C[_]](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, C],
      val attachedRelationship: Relationship[Join, To, J, _, T, Seq])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Seq, C, Seq]
    with ToManyRelationship[Owner, To, O, I1, T]

  /** A 'to one' relationship composed with a 'to one' relationship, which acts
    * as a 'to one' relationship.
    */
  class ToOneComposedToOne[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, Option],
      val attachedRelationship: Relationship[Join, To, J, _, T, Option])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Option, Option, Option]
    with ToOneRelationship[Owner, To, O, I1, T]

  /** Implicitly adds the compose operation to 'to one' relationships whose
    * target relation is itself an entity.
    *
    * @param relationship The 'to one' relationship onto which the compose
    *                     operation is implicitly added.
    *
    * @tparam From The relationship owner's table type.
    * @tparam Join The relationship target's table type.
    * @tparam O    The relationship owner's type.
    * @tparam J    The relationship target's type.
    * @tparam I1   The relationship owner's ID type.
    */
  implicit class ToOneComposable[From <: EntityTable[O, I1], Join <: EntityTable[J, _], O <: Entity[O, I1], J <: Entity[J, _], I1]
  (relationship: Relationship[From, Join, O, I1, J, Option]) {

    /** Returns a composed 'to one' relationship by attaching the given 'to one'
      * relationship onto the base relationship.
      *
      * @param target The 'to one' relationship to be composed with the base
      *               relationship.
      *
      * @tparam To The target relationship's target table type.
      * @tparam T  The target relationship's target record type.
      */
    def compose[To <: Table[T], T](target: Relationship[Join, To, J, _, T, Option])
    : ToOneComposedToOne[From, Join, To, O, J, T, I1] =
      new ToOneComposedToOne[From, Join, To, O, J, T, I1](relationship, target)

    /** Returns a composed 'to many' relationship by attaching the given
      * 'to many' relationship onto the base relationship.
      *
      * @param target The 'to many' relationship to be composed with the base
      *               relationship.
      *
      * @tparam To The target relationship's target table type.
      * @tparam T  The target relationship's target record type.
      */
    def compose[To <: Table[T], T](target: Relationship[Join, To, J, _, T, Seq])
    : ComposedToMany[From, Join, To, O, J, T, I1, Option] =
      new ComposedToMany[From, Join, To, O, J, T, I1, Option](relationship, target)
  }

  /** Implicitly adds the compose operation to 'to many' relationships whose
    * target relation is itself an entity.
    *
    * @param relationship The 'to many' relationship onto which the compose
    *                     operation is implicitly added.
    *
    * @tparam From The relationship owner's table type.
    * @tparam Join The relationship target's table type.
    * @tparam O    The relationship owner's type.
    * @tparam J    The relationship target's type.
    * @tparam I1   The relationship owner's ID type.
    */
  implicit class ToManyComposable[From <: EntityTable[O, I1], Join <: EntityTable[J, _], O <: Entity[O, I1], J <: Entity[J, _], I1]
  (relationship: Relationship[From, Join, O, I1, J, Seq]) {

    /** Returns a composed 'to many' relationship by attaching the given
      * 'to many' relationship onto the base relationship.
      *
      * @param target The 'to many' relationship to be composed with the base
      *               relationship.
      *
      * @tparam To The target relationship's target table type.
      * @tparam T  The target relationship's target record type.
      * @tparam C  The target relationship's value container type.
      */
    def compose[To <: Table[T], T, C[_]](target: Relationship[Join, To, J, _, T, C])
    : ToManyComposed[From, Join, To, O, J, T, I1, C] =
      new ToManyComposed[From, Join, To, O, J, T, I1, C](relationship, target)
  }
}
