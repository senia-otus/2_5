package ru.otus.sc.user.dao

import java.util.UUID

import ru.otus.sc.user.model.User

import scala.concurrent.Future

trait UserDao {
  def createUser(user: User): Future[User]
  def getUser(userId: UUID): Future[Option[User]]
  def updateUser(user: User): Future[Option[User]]
  def deleteUser(userId: UUID): Future[Option[User]]
  def findByLastName(lastName: String): Future[Seq[User]]
  def findAll(): Future[Seq[User]]
}
