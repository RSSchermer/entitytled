package entitytled

/** Base class for entities. Entities need to be uniquely identifiable by an ID. */
abstract class Entity {
  type IdType

  val id: Option[IdType]
}
