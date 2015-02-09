package entitytled

import monocle.Lens

trait RelationshipComponent {
  self: DriverComponent with TableComponent with RelationshipRepComponent =>

  import driver.simple._

  trait SideLoadable[T <: Table[M], M] {
    def sideLoadOn(instances: List[M], query: Query[T, M, Seq])(implicit session: Session): List[M]

    def sideLoadOn(instance: M, query: Query[T, M, Seq])(implicit session: Session): M
  }

  trait Relationship[From <: EntityTable[E], To <: Table[T], E <: Entity, T, Value, Rep <: RelationshipRep[E, Value]]
    extends SideLoadable[From, E]
  {
    val propertyLens: Lens[E, Rep]

    def setOn(instance: E, value: Value): E

    def queryFor(id: E#IdType): Query[To, T, Seq]

    def fetchFor(id: E#IdType)(implicit session: Session): Value

    def fetchFor(instance: E)(implicit session: Session): Value

    def fetchOn(instance: E)(implicit session: Session): E =
      setOn(instance, fetchFor(instance))

    def include(sideLoad: SideLoadable[To, T]*): Relationship[From, To, E, T, Value, Rep]
  }

  abstract class DirectRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity, T, Value, Rep <: RelationshipRep[E, Value]]
      (implicit mapping: BaseColumnType[E#IdType])
    extends Relationship[From, To, E, T, Value, Rep]
  {
    val fromQuery: Query[From, E, Seq]

    val toQuery: Query[To, T, Seq]

    val joinCondition: (From, To) => Column[Boolean]

    def queryFor(id: E#IdType): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2)
  }

  abstract class ThroughRelationship[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity, T, Value, Rep <: RelationshipRep[E, Value]]
      (implicit mapping: BaseColumnType[E#IdType])
    extends Relationship[From, To, E, T, Value, Rep]
  {
    val fromQuery: Query[From, E, Seq]

    val toQuery: Query[(Through, To), _ <: (_, T), Seq]

    val joinCondition: (From, (Through, To)) => Column[Boolean]

    def queryFor(id: E#IdType): Query[To, T, Seq] =
      fromQuery.filter(_.id === id).innerJoin(toQuery).on(joinCondition).map(_._2._2)
  }

  trait ToOneRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity, T] {
    self: Relationship[From, To, E, T, Option[T], One[E, T]] =>

    def setOn(instance: E, value: Option[T]): E =
      propertyLens.set(OneFetched(this, instance.id, value))(instance)

    def fetchFor(id: E#IdType)(implicit session: Session): Option[T] =
      queryFor(id).firstOption

    def fetchFor(instance: E)(implicit session: Session): Option[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => None
    }

    def include(sideLoad: SideLoadable[To, T]*): OneSideLoading[From, To, E, T] =
      new OneSideLoading(this, sideLoad)
  }

  trait ToManyRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity, T] {
    self: Relationship[From, To, E, T, Seq[T], Many[E, T]] =>

    def setOn(instance: E, values: Seq[T]): E =
      propertyLens.set(ManyFetched(this, instance.id, values))(instance)

    def fetchFor(id: E#IdType)(implicit session: Session): Seq[T] =
      queryFor(id).list

    def fetchFor(instance: E)(implicit session: Session): Seq[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => List()
    }

    def include(sideLoad: SideLoadable[To, T]*): ManySideLoading[From, To, E, T] =
      new ManySideLoading(this, sideLoad)
  }

  class ToOne[From <: EntityTable[E], To <: Table[T], E <: Entity, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean],
      val propertyLens: Lens[E, One[E, T]])(implicit mapping: BaseColumnType[E#IdType])
    extends DirectRelationship[From, To, E, T, Option[T], One[E, T]]
    with ToOneRelationship[From, To, E, T]
  {
    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, None)
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildMap(query).get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, None)
      }
    }

    private def buildMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] =
      query.innerJoin(toQuery).on(joinCondition).list.groupBy(_._1)
        .map(x => (x._1, x._2.map(_._2).headOption))
  }

  class ToMany[From <: EntityTable[E], To <: Table[T], E <: Entity, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[To, T, Seq],
      val joinCondition: (From, To) => Column[Boolean],
      val propertyLens: Lens[E, Many[E, T]])(implicit mapping: BaseColumnType[E#IdType])
    extends DirectRelationship[From, To, E, T, Seq[T], Many[E, T]]
    with ToManyRelationship[From, To, E, T]
  {
    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, Seq())
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildMap(query).get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, Seq())
      }
    }

    private def buildMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] =
      query.innerJoin(toQuery).on(joinCondition).list.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  class ToOneThrough[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean],
      val propertyLens: Lens[E, One[E, T]])(implicit mapping: BaseColumnType[E#IdType])
    extends ThroughRelationship[From, Through, To, E, T, Option[T], One[E, T]]
    with ToOneRelationship[From, To, E, T]
  {
    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, None)
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildMap(query).get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, None)
      }
    }

    private def buildMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Option[T]] =
      query.innerJoin(toQuery).on(joinCondition).map(x => (x._1, x._2._2)).list
        .groupBy(_._1).map(x => (x._1, x._2.map(_._2).headOption))
  }

  class ToManyThrough[From <: EntityTable[E], Through <: Table[_], To <: Table[T], E <: Entity, T](
      val fromQuery: Query[From, E, Seq],
      val toQuery: Query[(Through, To), _ <: (_, T), Seq],
      val joinCondition: (From, (Through, To)) => Column[Boolean],
      val propertyLens: Lens[E, Many[E, T]])(implicit mapping: BaseColumnType[E#IdType])
    extends ThroughRelationship[From, Through, To, E, T, Seq[T], Many[E, T]]
    with ToManyRelationship[From, To, E, T]
  {
    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] = {
      val map = buildMap(query)

      instances.map(instance => map.get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, Seq())
      })
    }

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E = {
      buildMap(query).get(instance) match {
        case Some(value) => setOn(instance, value)
        case _ => setOn(instance, Seq())
      }
    }

    private def buildMap(query: Query[From, E, Seq])(implicit session: Session): Map[E, Seq[T]] =
      query.innerJoin(toQuery).on(joinCondition).map(x => (x._1, x._2._2)).list
        .groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  abstract class WrappingRelationship[From <: EntityTable[E], To <: Table[T], E <: Entity, T, Value, Rep <: RelationshipRep[E, Value]](
      val relationship: Relationship[From, To, E, T, Value, Rep])
    extends Relationship[From, To, E, T, Value, Rep]
  {
    val propertyLens: Lens[E, Rep] = relationship.propertyLens

    def setOn(instance: E, value: Value): E =
      relationship.setOn(instance, value)

    def queryFor(id: E#IdType): Query[To, T, Seq] =
      relationship.queryFor(id)

    def fetchFor(id: E#IdType)(implicit session: Session): Value =
      relationship.fetchFor(id)

    def fetchFor(instance: E)(implicit session: Session): Value =
      relationship.fetchFor(instance)

    def sideLoadOn(instances: List[E], query: Query[From, E, Seq])(implicit session: Session): List[E] =
      relationship.sideLoadOn(instances, query)

    def sideLoadOn(instance: E, query: Query[From, E, Seq])(implicit session: Session): E =
      relationship.sideLoadOn(instance, query)
  }

  class OneSideLoading[From <: EntityTable[E], To <: Table[T], E <: Entity, T](
      override val relationship: Relationship[From, To, E, T, Option[T], One[E, T]],
      val sideLoads: Seq[SideLoadable[To, T]])
    extends WrappingRelationship[From, To, E, T, Option[T], One[E, T]](relationship)
  {
    override def fetchFor(id: E#IdType)(implicit session: Session): Option[T] =
      relationship.fetchFor(id) match {
        case Some(instance) =>
          val toQuery = relationship.queryFor(id)
          Some(sideLoads.foldLeft(instance)((i, s) => s.sideLoadOn(i, toQuery)))
        case _ => None
      }

    override def fetchFor(instance: E)(implicit session: Session): Option[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => None
    }

    def include(sideLoad: SideLoadable[To, T]*): OneSideLoading[From, To, E, T] =
      new OneSideLoading(relationship, sideLoads ++ sideLoad)
  }

  class ManySideLoading[From <: EntityTable[E], To <: Table[T], E <: Entity, T](
      override val relationship: Relationship[From, To, E, T, Seq[T], Many[E, T]],
      val sideLoads: Seq[SideLoadable[To, T]])
    extends WrappingRelationship[From, To, E, T, Seq[T], Many[E, T]](relationship)
  {
    override def fetchFor(id: E#IdType)(implicit session: Session): Seq[T] = {
      val toQuery = relationship.queryFor(id)
      sideLoads.foldLeft(relationship.fetchFor(id).toList)((i, s) => s.sideLoadOn(i, toQuery))
    }

    override def fetchFor(instance: E)(implicit session: Session): Seq[T] = instance.id match {
      case Some(id) => fetchFor(id)
      case _ => Seq()
    }

    def include(sideLoad: SideLoadable[To, T]*): ManySideLoading[From, To, E, T] =
      new ManySideLoading(relationship, sideLoads ++ sideLoad)
  }
}
