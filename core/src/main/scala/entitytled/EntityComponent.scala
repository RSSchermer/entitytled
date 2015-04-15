package entitytled

import scala.language.experimental.macros
import scala.reflect.macros._

trait EntityComponent {
  self: DriverComponent with RelationshipComponent with RelationshipRepComponent =>
  
  import driver.simple._

  type Includes[E <: Entity[E, _]] = Map[Relationship[_ <: EntityTable[E, _], _ <: Table[_], E, _, _, _], Any]

  /** Base class for entities. Entities need to be uniquely identifiable by an ID. */
  abstract class Entity[E <: Entity[E, I], I](implicit val includes: Includes[E], includesSetter: IncludesSetter[E]) {
    val id: Option[I]

    def withIncludes(includes: Includes[E]): E =
      includesSetter.withIncludes(this.asInstanceOf[E], includes)

    def setInclude[T, V](
        relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, V],
        value: V): E =
      withIncludes(includes + (relationship -> value))

    def setInclude[T, V](relationshipRep: RelationshipRep[E, I, T, V] with Fetched[V]): E =
      setInclude(relationshipRep.relationship, relationshipRep.value)

    def one[T](relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Option[T]]): One[E, I, T] =
      includes.get(relationship) match {
        case Some(value) =>
          OneFetched[E, I, T](relationship, value.asInstanceOf[Option[T]], id.asInstanceOf[Option[I]])
        case _ =>
          OneUnfetched[E, I, T](relationship, id.asInstanceOf[Option[I]])
      }

    def many[T](relationship: Relationship[_ <: EntityTable[E, I], _ <: Table[T], E, I, T, Seq[T]]): Many[E, I, T] =
      includes.get(relationship) match {
        case Some(value) =>
          ManyFetched[E, I, T](relationship, value.asInstanceOf[Seq[T]], id.asInstanceOf[Option[I]])
        case _ =>
          ManyUnfetched[E, I, T](relationship, id.asInstanceOf[Option[I]])
      }
  }

  trait IncludesSetter[E <: Entity[E, _]] {
    def withIncludes(instance: E, includes: Includes[E]): E
  }

  object IncludesSetter {
    implicit def materializeIncludesSetter[E <: Entity[E, _]]: IncludesSetter[E] =
      macro MaterializeIncludesSetterImpl.apply[E]
  }

  /** Base class for entity tables */
  abstract class EntityTable[E <: Entity[E, I], I](
      tag: Tag,
      schemaName: Option[String],
      tableName: String)(implicit val colType: BaseColumnType[I])
    extends Table[E](tag, schemaName, tableName)
  {
    def this(tag: Tag, tableName: String)(implicit mapping: BaseColumnType[I]) =
      this(tag, None, tableName)

    def id: Column[I]
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
