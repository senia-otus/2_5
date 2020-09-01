package ru.otus.sc.user.service.impl

import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.model.{
  CreateUserRequest,
  CreateUserResponse,
  DeleteUserRequest,
  DeleteUserResponse,
  FindUsersRequest,
  FindUsersResponse,
  GetUserRequest,
  GetUserResponse,
  UpdateUserRequest,
  UpdateUserResponse
}
import ru.otus.sc.user.service.UserService

import scala.concurrent.{ExecutionContext, Future}

class UserServiceImpl(dao: UserDao)(implicit ec: ExecutionContext) extends UserService {
  def createUser(request: CreateUserRequest): Future[CreateUserResponse] =
    dao.createUser(request.user).map(CreateUserResponse)

  def getUser(request: GetUserRequest): Future[GetUserResponse] =
    dao.getUser(request.userId) map {
      case Some(user) => GetUserResponse.Found(user)
      case None       => GetUserResponse.NotFound(request.userId)
    }

  def updateUser(request: UpdateUserRequest): Future[UpdateUserResponse] =
    request.user.id match {
      case None => Future.successful(UpdateUserResponse.CantUpdateUserWithoutId)
      case Some(userId) =>
        dao.updateUser(request.user) map {
          case Some(user) => UpdateUserResponse.Updated(user)
          case None       => UpdateUserResponse.NotFound(userId)
        }
    }

  def deleteUser(request: DeleteUserRequest): Future[DeleteUserResponse] =
    dao
      .deleteUser(request.userId)
      .map {
        _.map(DeleteUserResponse.Deleted)
          .getOrElse(DeleteUserResponse.NotFound(request.userId))
      }

  def findUsers(request: FindUsersRequest): Future[FindUsersResponse] =
    request match {
      case FindUsersRequest.ByLastName(lastName) =>
        dao.findByLastName(lastName).map(FindUsersResponse.Result)
    }
}
