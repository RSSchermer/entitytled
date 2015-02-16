package entitytled

trait TableComponent {
  self: DriverComponent =>

  import driver.simple._

  /** Base class for entity tables */
  abstract class EntityTable[E <: Entity](
      tag: Tag,
      schemaName: Option[String],
      tableName: String)(implicit val colType: BaseColumnType[E#IdType])
    extends Table[E](tag, schemaName, tableName)
  {
    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[E#IdType]) =
      this(tag, None, tableName)

    def id: Column[E#IdType]
  }
}
