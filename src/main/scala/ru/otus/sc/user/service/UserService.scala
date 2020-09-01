package ru.otus.sc.user.service

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

import scala.concurrent.Future

trait UserService {
  def createUser(request: CreateUserRequest): Future[CreateUserResponse]
  def getUser(request: GetUserRequest): Future[GetUserResponse]
  def updateUser(request: UpdateUserRequest): Future[UpdateUserResponse]
  def deleteUser(request: DeleteUserRequest): Future[DeleteUserResponse]
  def findUsers(request: FindUsersRequest): Future[FindUsersResponse]
}
