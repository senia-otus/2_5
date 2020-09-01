package ru.otus.sc.user.dao.map

import ru.otus.sc.user.dao.{UserDao, UserDaoTest}

import scala.concurrent.ExecutionContext.Implicits.global

class UserDaoMapImplTest extends UserDaoTest("UserDaoMapImplTest") {
  def createEmptyDao(): UserDao = new UserDaoMapImpl
}
