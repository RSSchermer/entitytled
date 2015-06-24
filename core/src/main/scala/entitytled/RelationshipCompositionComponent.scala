package entitytled

import scala.language.higherKinds
import scala.language.existentials

trait RelationshipCompositionComponent {
  self: DriverComponent with EntityComponent with RelationshipComponent =>

  import driver.api._

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

  class ToManyComposed[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1, C[_]](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, Seq],
      val attachedRelationship: Relationship[Join, To, J, _, T, C])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Seq, Seq, C]
    with ToManyRelationship[Owner, To, O, I1, T]

  class ComposedToMany[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1, C[_]](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, C],
      val attachedRelationship: Relationship[Join, To, J, _, T, Seq])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Seq, C, Seq]
    with ToManyRelationship[Owner, To, O, I1, T]

  class ToOneComposedToOne[Owner <: EntityTable[O, I1], Join <: EntityTable[J, _], To <: Table[T], O <: Entity[O, I1], J <: Entity[J, _], T, I1](
      val baseRelationship: Relationship[Owner, Join, O, I1, J, Option],
      val attachedRelationship: Relationship[Join, To, J, _, T, Option])
    extends ComposedRelationship[Owner, Join, To, O, J, T, I1, Option, Option, Option]
    with ToOneRelationship[Owner, To, O, I1, T]

  implicit class ToOneComposable[From <: EntityTable[O, I1], Join <: EntityTable[J, _], O <: Entity[O, I1], J <: Entity[J, _], I1]
  (relationship: Relationship[From, Join, O, I1, J, Option]) {
    def compose[To <: Table[T], T](target: Relationship[Join, To, J, _, T, Option])
    : ToOneComposedToOne[From, Join, To, O, J, T, I1] =
      new ToOneComposedToOne[From, Join, To, O, J, T, I1](relationship, target)

    def compose[To <: Table[T], T](target: Relationship[Join, To, J, _, T, Seq])
    : ComposedToMany[From, Join, To, O, J, T, I1, Option] =
      new ComposedToMany[From, Join, To, O, J, T, I1, Option](relationship, target)
  }

  implicit class ToManyComposable[From <: EntityTable[O, I1], Join <: EntityTable[J, _], O <: Entity[O, I1], J <: Entity[J, _], I1]
  (relationship: Relationship[From, Join, O, I1, J, Seq]) {
    def compose[To <: Table[T], T, C[_]](target: Relationship[Join, To, J, _, T, C])
    : ToManyComposed[From, Join, To, O, J, T, I1, C] =
      new ToManyComposed[From, Join, To, O, J, T, I1, C](relationship, target)
  }
}
