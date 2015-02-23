package entitytled

trait EntityComponent {
  self: DriverComponent with RelationshipComponent with RelationshipRepComponent =>
  
  import driver.simple._

  case class Include[E <: Entity[E], T, V](
      relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, V],
      value: V)

  type Includes[E <: Entity[E]] = Seq[Include[E, _, _]]
  
  /** Base class for entities. Entities need to be uniquely identifiable by an ID. */
  abstract class Entity[E <: Entity[E]](implicit val includes: Includes[E]) {
    type IdType

    val id: Option[IdType]

    def withIncludes(includes: Includes[E]): E

    def addInclude[T, V](include: Include[E, T, V]): E =
      withIncludes(includes :+ include)

    def one[T](relationship: ToOneRelationship[_ <: EntityTable[E], _ <: Table[T], E, T]): One[E, T] =
      includes.find(_.relationship == relationship) match {
        case Some(include) =>
          OneFetched[E, T](relationship, include.value.asInstanceOf[Option[T]], id.asInstanceOf[Option[E#IdType]])
        case _ =>
          OneUnfetched[E, T](relationship, id.asInstanceOf[Option[E#IdType]])
      }

    def many[T](relationship: ToManyRelationship[_ <: EntityTable[E], _ <: Table[T], E, T]): Many[E, T] =
      includes.find(_.relationship == relationship) match {
        case Some(include) =>
          ManyFetched[E, T](relationship, include.value.asInstanceOf[Seq[T]], id.asInstanceOf[Option[E#IdType]])
        case _ =>
          ManyUnfetched[E, T](relationship, id.asInstanceOf[Option[E#IdType]])
      }
  }

  /** Base class for entity tables */
  abstract class EntityTable[E <: Entity[E]](
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
