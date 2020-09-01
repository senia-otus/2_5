package ru.otus.sc.user.model

import java.util.UUID

case class UpdateUserRequest(user: User)

sealed trait UpdateUserResponse
object UpdateUserResponse {
  final case class Updated(user: User)      extends UpdateUserResponse
  final case class NotFound(userId: UUID)   extends UpdateUserResponse
  final case object CantUpdateUserWithoutId extends UpdateUserResponse
}
