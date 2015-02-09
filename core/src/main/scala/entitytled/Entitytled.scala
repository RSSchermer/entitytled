package entitytled

trait Entitytled extends DriverComponent
  with TableComponent
  with EntityBuilderComponent
  with EntityRepositoryComponent
  with RelationshipComponent
  with RelationshipRepComponent
  with EntityCompanionComponent
{
  type Entity = entitytled.Entity
}
