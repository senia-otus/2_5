package ru.otus.sc.user.dao.quill

import java.util.UUID

import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.quill.DoobieContext
import doobie.util.transactor.Transactor
import io.getquill.{idiom => _, _}
import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.dao.quill.UserDaoQuillImpl._
import ru.otus.sc.user.model.{Role, User}
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent.{ExecutionContext, Future}

class UserDaoQuillImpl(tr: Transactor[IO])(implicit ec: ExecutionContext) extends UserDao {
  private val dc = new DoobieContext.Postgres(SnakeCase)
  import dc._

  private val users        = quote { querySchema[UserRow]("users") }
  private val usersToRoles = quote { query[UsersToRoles] }

  private def insertRoles(userId: UUID, roles: Set[Role]) =
    run {
      liftQuery(roles.toList.map(UsersToRoles(userId, _))).foreach { r =>
        query[UsersToRoles].insert(r)
      }
    }

  def createUser(user: User): Future[User] = {
    val insertUser = run {
      quote {
        users.insert(lift(UserRow.fromUser(user))).returningGenerated(_.id)
      }
    }

    val res =
      for {
        newId <- insertUser
        _     <- insertRoles(newId, user.roles)
      } yield user.copy(id = Some(newId))

    res
      .transact(tr)
      .unsafeToFuture()
  }

  private def selectUser(userId: UUID, forUpdate: Boolean): ConnectionIO[Option[UserRow]] = {
    if (forUpdate)
      sql"""select u.id, u.first_name, u.last_name, u.age
         from users u
         where u.id = $userId
       for update
       """.query[UserRow].option
    else run { quote { users.filter(_.id == lift(userId)) } }.map(_.headOption)

  }

  private def selectRoles(userId: UUID) =
    run {
      quote {
        usersToRoles.filter(_.usersId == lift(userId)).map(_.rolesCode)
      }
    }.map(_.toSet)

  def getUser(userId: UUID): Future[Option[User]] = {
    val res = for {
      u  <- selectUser(userId, forUpdate = false)
      rs <- selectRoles(userId)
    } yield u.map(_.toUser(rs))

    res.transact(tr).unsafeToFuture()
  }

  def updateUser(user: User): Future[Option[User]] = {
    user match {
      case User(Some(userId), firstName, lastName, age, roles) =>
        val update = run {
          quote {
            users
              .filter(_.id == lift(userId))
              .update(
                _.firstName -> lift(firstName),
                _.lastName  -> lift(lastName),
                _.age       -> lift(age)
              )
          }
        }

        val deleteRoles = run {
          quote {
            usersToRoles.filter(_.usersId == lift(userId)).delete
          }
        }

        val insertRolesIO = insertRoles(userId, roles)

        val res = for {
          u <- selectUser(userId, forUpdate = true)
          _ <- u match {
            case Some(_) => update *> deleteRoles *> insertRolesIO
            case None    => ().pure[ConnectionIO]
          }
        } yield u

        res.transact(tr).unsafeToFuture().map(_.map(_ => user))
      case _ => Future.successful(None)
    }
  }

  def deleteUser(userId: UUID): Future[Option[User]] = {
    val res = for {
      u     <- selectUser(userId, forUpdate = true)
      roles <- selectRoles(userId)
      _ <- u match {
        case Some(UserRow(id, _, _, _)) =>
          val deleteRoles =
            run {
              quote {
                usersToRoles.filter(_.usersId == lift(userId)).delete
              }
            }
          val deleteUser = run {
            quote {
              users.filter(_.id == lift(id)).delete
            }
          }

          deleteRoles *> deleteUser
        case None => ().pure[ConnectionIO]
      }
    } yield u.map(_.toUser(roles))

    res.transact(tr).unsafeToFuture()
  }

  private def groupUsers(rows: Seq[(UserRow, Option[UsersToRoles])]) =
    rows
      .groupBy(_._1)
      .view
      .mapValues(_.flatMap(_._2))
      .map { case (u, roles) => u.toUser(roles.map(_.rolesCode).toSet) }
      .toVector

  def findByLastName(lastName: String): Future[Seq[User]] = {
    val res =
      run {
        quote {
          for {
            u <- users if u.lastName == lift(lastName)
            r <- usersToRoles.leftJoin(_.usersId == u.id)
          } yield (u, r)
        }
      }

    res
      .map(groupUsers)
      .transact(tr)
      .unsafeToFuture()
  }

  def findAll(): Future[Seq[User]] = {
    val res =
      run {
        quote {
          for {
            u <- users
            r <- usersToRoles.leftJoin(_.usersId == u.id)
          } yield (u, r)
        }
      }

    res
      .map(groupUsers)
      .transact(tr)
      .unsafeToFuture()
  }

  private[quill] def deleteAll(): Future[Unit] = {
    val res =
      run(quote(usersToRoles.filter(_ => lift(true)).delete)) *>
        run(quote(users.filter(_ => lift(true)).delete))

    res.transact(tr).unsafeToFuture().map(_ => ())
  }
}

object UserDaoQuillImpl {
  case class UserRow(
      id: UUID,
      firstName: String,
      lastName: String,
      age: Int
  ) {
    def toUser(roles: Set[Role]): User = User(Some(id), firstName, lastName, age, roles)
  }

  object UserRow {
    def fromUser(user: User): UserRow =
      UserRow(user.id.getOrElse(UUID.randomUUID()), user.firstName, user.lastName, user.age)
  }

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

  implicit lazy val encodeRole: MappedEncoding[Role, String] =
    MappedEncoding[Role, String](roleToCode)
  implicit lazy val decodeRole: MappedEncoding[String, Role] =
    MappedEncoding[String, Role](roleFromCode)

  implicit val roleMeta: Meta[Role] = Meta[String].imap(roleFromCode)(roleToCode)

  case class UsersToRoles(usersId: UUID, rolesCode: Role)
}
