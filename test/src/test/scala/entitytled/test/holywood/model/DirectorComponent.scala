package entitytled.test.holywood.model

import entitytled.Entitytled

trait DirectorComponent {
  self: Entitytled with MovieComponent with StarComponent =>

  import driver.api._

  case class DirectorID(value: Long) extends MappedTo[Long]

  case class Director(
      id: Option[DirectorID],
      name: String)(implicit includes: Includes[Director])
    extends Entity[Director, DirectorID]
  {
    val movies = many(Director.movies)
    val stars = many(Director.stars)
  }

  object Director extends EntityCompanion[Directors, Director, DirectorID] {
    val movies = toMany[Movies, Movie]
    val stars = movies compose Movie.stars
  }

  class Directors(tag: Tag) extends EntityTable[Director, DirectorID](tag, "DIRECTORS") {
    def id = column[DirectorID]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")

    def * = (id.?, name) <>((Director.apply _).tupled, Director.unapply)
  }
}
