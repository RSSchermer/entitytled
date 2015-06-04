package entitytled

/** Extend this trait to create an Entitytled profile.
  *
  * This trait is Entitytled's main entry point. Clients extending this trait
  * need to define the abstract `driver` member by providing a
  * [[slick.driver.JdbcDriver]].
  */
trait Entitytled extends DriverComponent
  with EntityComponent
  with EntityActionBuilderComponent
  with EntityRepositoryComponent
  with RelationshipComponent
  with RelationshipRepComponent
  with EntityCompanionComponent
  with EntityActionBuilderConversionsComponent
  with RelationshipRepConversionsComponent
  with EntityRepositoryConversionsComponent
