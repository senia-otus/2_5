package ru.otus.sc.user.dao.map

import java.util.UUID

import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.model.User

import scala.concurrent.{ExecutionContext, Future}

class UserDaoMapImpl(implicit ec: ExecutionContext) extends UserDao {
  private var users: Map[UUID, User] = Map.empty

  def createUser(user: User): Future[User] =
    Future {
      val id         = UUID.randomUUID()
      val userWithId = user.copy(id = Some(id))
      users += (id -> userWithId)
      userWithId
    }

  def getUser(userId: UUID): Future[Option[User]] = Future { users.get(userId) }

  def updateUser(user: User): Future[Option[User]] =
    Future {
      for {
        id <- user.id
        _  <- users.get(id)
      } yield {
        users += (id -> user)
        user
      }
    }

  def deleteUser(userId: UUID): Future[Option[User]] =
    Future {
      users.get(userId) match {
        case Some(user) =>
          users -= userId
          Some(user)
        case None => None
      }
    }

  def findByLastName(lastName: String): Future[Seq[User]] =
    Future {
      users.values.filter(_.lastName == lastName).toVector
    }

  def findAll(): Future[Seq[User]] = Future { users.values.toVector }
}
