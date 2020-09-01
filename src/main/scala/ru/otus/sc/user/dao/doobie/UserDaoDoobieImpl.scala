package ru.otus.sc.user.dao.doobie

import java.util.UUID

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.dao.doobie.UserDaoDoobieImpl._
import ru.otus.sc.user.model.{Role, User}
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent.{ExecutionContext, Future}

/*
 * Fragment(sql:  String, params: Seq[TypedParams])
 *
 * Query: Fragment + парсер строки
 *
 * ConnectionIO: запросы (Query, Update), комбинация ConnectionIO и действий
 *
 * IO[T]: () => T
 *
 * FRM:
 * Doobie - нет DSL, поддержки разных БД
 * Quill - все плохо динамическим SQL, но есть поддержка разных БД
 * Doobie + Quill - нет поддержки разных БД
 * Slick - есть динамический SQL, есть поддержка разных БД, cамый богатый DSL - версия от 2019
 * jOOQ - DSL самый близкий к SQL, самая полная реализация SQL (влючая различные БД), поддержка, проблемы с Типизацией
 * zio-sql (не известно когда и что)
 * */
class UserDaoDoobieImpl(tr: Transactor[IO])(implicit ec: ExecutionContext) extends UserDao {
  private def insertRoles(userId: UUID, roles: Set[Role]): ConnectionIO[Int] = {
    val sql = "insert into users_to_roles (users_id, roles_code) values (?, ?)"

    Update[UserToRole](sql).updateMany(roles.toList.map(UserToRole(userId, _)))

  }

  def createUser(user: User): Future[User] = {
    val insertUser =
      sql"""insert into users(first_name, last_name, age)
            values (${user.firstName}, ${user.lastName}, ${user.age})""".update
        .withGeneratedKeys[UUID]("id")
        .compile
        .lastOrError

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
    val base = fr"""select u.id, u.first_name, u.last_name, u.age
         from users u
         where u.id = $userId
       """

    val sql = if (forUpdate) base ++ fr"for update" else base

    sql.query[UserRow].option
  }

  private def selectRoles(userId: UUID) =
    sql"""select u2r.roles_code from users_to_roles u2r where u2r.users_id = $userId"""
      .query[Role]
      .to[Set]

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
        val update =
          sql"""
            update users
            set first_name = $firstName,
                last_name = $lastName,
                age = $age
            where id = $userId
             """.update.run

        val deleteRoles =
          sql"delete from users_to_roles u2r where u2r.users_id = $userId".update.run

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
            sql"""delete from users_to_roles u2r where u2r.users_id = $id""".update.run
          val deleteUser = sql"""delete from users u where u.id = $id""".update.run

          deleteRoles *> deleteUser
        case None => ().pure[ConnectionIO]
      }
    } yield u.map(_.toUser(roles))

    res.transact(tr).unsafeToFuture()
  }

  private def selectByCondition(condition: Option[Fragment]) = {
    val base =
      sql"""
           select u.id, u.first_name, u.last_name, u.age, u2r.roles_code
           from users u
           left join users_to_roles u2r on u.id = u2r.users_id"""

    val sql = condition match {
      case Some(cond) => base ++ fr" where " ++ cond
      case None       => base
    }

    sql
      .query[(UserRow, Option[Role])]
      .to[Vector]
      .map(
        _.groupBy(_._1).view
          .mapValues(_.map(_._2))
          .map { case (u, roles) => u.toUser(roles.flatten.toSet) }
          .toVector
      )
      .transact(tr)
      .unsafeToFuture()

  }

  def findByLastName(lastName: String): Future[Seq[User]] =
    selectByCondition(Some {
      fr"u.last_name = $lastName"
    })

  def findAll(): Future[Seq[User]] = selectByCondition(None)

  private[doobie] def deleteAll(): Future[Unit] = {
    val res =
      sql"""delete from users_to_roles where 1 = 1""".update.run *>
        sql"""delete from users where 1 = 1""".update.run

    res.transact(tr).unsafeToFuture().map(_ => ())
  }
}

object UserDaoDoobieImpl {
  case class UserRow(
      id: UUID,
      firstName: String,
      lastName: String,
      age: Int
  ) {
    def toUser(roles: Set[Role]): User = User(Some(id), firstName, lastName, age, roles)
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

  implicit val roleMeta: Meta[Role] = Meta[String].imap(roleFromCode)(roleToCode)

  case class UserToRole(userId: UUID, role: Role)
}
