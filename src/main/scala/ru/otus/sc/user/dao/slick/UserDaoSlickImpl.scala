package ru.otus.sc.user.dao.slick

import java.util.UUID

import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.model.{Role, User}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class UserDaoSlickImpl(db: Database)(implicit ec: ExecutionContext) extends UserDao {

  import UserDaoSlickImpl._

  def getUser(userId: UUID): Future[Option[User]] = {
    val res = for {
      user  <- users.filter(user => user.id === userId).result.headOption
      roles <- usersToRoles.filter(_.usersId === userId).map(_.rolesCode).result.map(_.toSet)
    } yield user.map(_.toUser(roles))

    db.run(res)
  }

  def createUser(user: User): Future[User] = {
    val userRaw = UserRow.fromUser(user).copy(id = None)

    val res = for {
      newId <- (users returning users.map(_.id)) += userRaw
      _     <- usersToRoles ++= user.roles.map(newId -> _)
    } yield user.copy(id = Some(newId))

    db.run(res.transactionally)
  }

  def updateUser(user: User): Future[Option[User]] =
    user.id match {
      case Some(userId) =>
        val updateUser =
          users
            .filter(_.id === userId)
            .map(u => (u.firstName, u.lastName, u.age))
            .update((user.firstName, user.lastName, user.age))

        val deleteRoles = usersToRoles.filter(_.usersId === userId).delete
        val insertRoles = usersToRoles ++= user.roles.map(userId -> _)

        val action =
          for {
            u <- users.filter(_.id === userId).forUpdate.result.headOption
            _ <- u match {
              case None    => DBIO.successful(())
              case Some(_) => updateUser >> deleteRoles >> insertRoles
            }
          } yield u.map(_ => user)

        db.run(action.transactionally)

      case None => Future.successful(None)
    }

  def deleteUser(userId: UUID): Future[Option[User]] = {
    val action =
      for {
        u <- users.filter(_.id === userId).forUpdate.result.headOption
        res <- u match {
          case None => DBIO.successful(None)
          case Some(userRow) =>
            val rolesQuery = usersToRoles.filter(_.usersId === userId)
            for {
              roles <- rolesQuery.map(_.rolesCode).result
              _     <- rolesQuery.delete
              _     <- users.filter(_.id === userId).delete
            } yield Some(userRow.toUser(roles.toSet))
        }
      } yield res

    db.run(action.transactionally)
  }

  private def findByCondition(condition: Users => Rep[Boolean]): Future[Vector[User]] =
    db.run(users.filter(condition).joinLeft(usersToRoles).on(_.id === _.usersId).result)
      .map(_.groupMap(_._1)(_._2).view.map {
        case (user, roles) => user.toUser(roles.flatMap(_.map(_._2)).toSet)
      }.toVector)

  def findByLastName(lastName: String): Future[Seq[User]] = findByCondition(_.lastName === lastName)

  def findAll(): Future[Seq[User]] = findByCondition(_ => true)

  private[slick] def deleteAll(): Future[Unit] =
    db.run(usersToRoles.delete >> users.delete >> DBIO.successful(()))
}

object UserDaoSlickImpl {
  implicit val rolesType: BaseColumnType[Role] =
    MappedColumnType.base[Role, String](roleToCode, roleFromCode)

  def roleToCode(role: Role): String =
    role match {
      case Role.Reader  => "reader"
      case Role.Manager => "manager"
      case Role.Admin   => "admin"
    }

  def roleFromCode(code: String): Role =
    Option(code)
      .collect {
        case "reader"  => Role.Reader
        case "manager" => Role.Manager
        case "admin"   => Role.Admin
      }
      .getOrElse(throw new RuntimeException(s"Unsupported role code $code"))

  case class UserRow(
      id: Option[UUID],
      firstName: String,
      lastName: String,
      age: Int
  ) {
    def toUser(roles: Set[Role]): User = User(id, firstName, lastName, age, roles)
  }

  object UserRow extends ((Option[UUID], String, String, Int) => UserRow) {
    def fromUser(user: User): UserRow = UserRow(user.id, user.firstName, user.lastName, user.age)
  }

  class Users(tag: Tag) extends Table[UserRow](tag, "users") {
    val id        = column[UUID]("id", O.PrimaryKey, O.AutoInc)
    val firstName = column[String]("first_name")
    val lastName  = column[String]("last_name")
    val age       = column[Int]("age")

    val * = (id.?, firstName, lastName, age).mapTo[UserRow]
  }

  val users = TableQuery[Users]

  class UsersToRoles(tag: Tag) extends Table[(UUID, Role)](tag, "users_to_roles") {
    val usersId   = column[UUID]("users_id")
    val rolesCode = column[Role]("roles_code")

    val * = (usersId, rolesCode)
  }

  val usersToRoles = TableQuery[UsersToRoles]
}
