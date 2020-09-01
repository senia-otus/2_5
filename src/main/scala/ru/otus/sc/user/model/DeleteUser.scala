package ru.otus.sc.user.model

import java.util.UUID

case class DeleteUserRequest(userId: UUID)

sealed trait DeleteUserResponse
object DeleteUserResponse {
  case class Deleted(user: User)    extends DeleteUserResponse
  case class NotFound(userId: UUID) extends DeleteUserResponse
}
