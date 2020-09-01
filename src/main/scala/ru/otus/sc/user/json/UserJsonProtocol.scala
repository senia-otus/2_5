package ru.otus.sc.user.json

import play.api.libs.json.{Format, Json, OFormat}
import ru.otus.sc.json.AdtProtocol
import ru.otus.sc.user.model.{Role, UpdateUserResponse, User}

trait UserJsonProtocol extends AdtProtocol {
  implicit lazy val userFormat: OFormat[User] = Json.format

  implicit lazy val roleFormat: OFormat[Role] = {
    implicit val readerFormat: OFormat[Role.Reader.type]   = objectFormat(Role.Reader)
    implicit val managerFormat: OFormat[Role.Manager.type] = objectFormat(Role.Manager)
    implicit val adminFormat: OFormat[Role.Admin.type]     = objectFormat(Role.Admin)

    adtFormat("$type")(
      adtCase[Role.Reader.type]("Reader"),
      adtCase[Role.Manager.type]("Manager"),
      adtCase[Role.Admin.type]("Admin")
    )
  }

  implicit lazy val updateUserResponseFormat: Format[UpdateUserResponse] = {
    implicit val updatedFormat: OFormat[UpdateUserResponse.Updated]   = Json.format
    implicit val notFoundFormat: OFormat[UpdateUserResponse.NotFound] = Json.format
    implicit val cantUpdateUserWithoutIdFormat
        : OFormat[UpdateUserResponse.CantUpdateUserWithoutId.type] =
      objectFormat(UpdateUserResponse.CantUpdateUserWithoutId)

    adtFormat("$type")(
      adtCase[UpdateUserResponse.Updated]("Updated"),
      adtCase[UpdateUserResponse.NotFound]("NotFound"),
      adtCase[UpdateUserResponse.CantUpdateUserWithoutId.type]("CantUpdateUserWithoutId")
    )
  }
}

object UserJsonProtocol extends UserJsonProtocol
