package entitytled

abstract class Entity {
  type IdType

  val id: Option[IdType]
}
