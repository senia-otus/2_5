package ru.otus.sc.user.model

import java.util.UUID

case class GetUserRequest(userId: UUID)

sealed trait GetUserResponse
object GetUserResponse {
  case class Found(user: User)      extends GetUserResponse
  case class NotFound(userId: UUID) extends GetUserResponse
}
