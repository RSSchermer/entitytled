package entitytled

import scala.reflect.runtime.{universe => ru}

trait EntityComponent {
  self: DriverComponent with RelationshipComponent with RelationshipRepComponent =>
  
  import driver.simple._

  type Includes[E <: Entity[E]] = Map[Relationship[_ <: EntityTable[E], _ <: Table[_], E, _, _], Any]
  
  /** Base class for entities. Entities need to be uniquely identifiable by an ID. */
  abstract class Entity[E <: Entity[E]](implicit val includes: Includes[E]) {
    type IdType

    val id: Option[IdType]

    def withIncludes(includes: Includes[E]): E = {
      val m = ru.runtimeMirror(getClass.getClassLoader)
      val instanceMirror = m.reflect(this)
      val typeSignature = instanceMirror.symbol.typeSignature
      val copyMethod = typeSignature.member(ru.newTermName("copy")).asMethod
      val copyMirror = instanceMirror.reflectMethod(copyMethod)

      def valueFor(p: ru.Symbol, i: Int): Any = {
        val defaultArg = typeSignature.member(ru.newTermName(s"copy$$default$$${i+1}"))
        instanceMirror.reflectMethod(defaultArg.asMethod)()
      }

      val primaryArgs = copyMethod.paramss.head.zipWithIndex.map(p => valueFor(p._1, p._2))
      val args = primaryArgs :+ includes
      copyMirror(args: _*).asInstanceOf[E]
    }

    def setInclude[T, V](relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, V], value: V): E =
      withIncludes(includes + (relationship -> value))

    def setInclude[T, V](relationshipRep: RelationshipRep[E, T, V] with Fetched[V]): E =
      setInclude(relationshipRep.relationship, relationshipRep.value)

    def one[T](relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Option[T]]): One[E, T] =
      includes.get(relationship) match {
        case Some(value) =>
          OneFetched[E, T](relationship, value.asInstanceOf[Option[T]], id.asInstanceOf[Option[E#IdType]])
        case _ =>
          OneUnfetched[E, T](relationship, id.asInstanceOf[Option[E#IdType]])
      }

    def many[T](relationship: Relationship[_ <: EntityTable[E], _ <: Table[T], E, T, Seq[T]]): Many[E, T] =
      includes.get(relationship) match {
        case Some(value) =>
          ManyFetched[E, T](relationship, value.asInstanceOf[Seq[T]], id.asInstanceOf[Option[E#IdType]])
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
