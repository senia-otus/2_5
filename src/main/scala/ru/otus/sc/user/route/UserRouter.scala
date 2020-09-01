package ru.otus.sc.user.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import ru.otus.sc.route.BaseRouter
import ru.otus.sc.user.model.{CreateUserRequest, GetUserRequest, GetUserResponse, User}
import ru.otus.sc.user.service.UserService
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import ru.otus.sc.user.json.UserJsonProtocol._

class UserRouter(service: UserService) extends BaseRouter {
  def route: Route =
    pathPrefix("user") {
      getUser ~
        createUser
    }

  private val UserIdRequest = JavaUUID.map(GetUserRequest)

  private def getUser: Route =
    (get & path(UserIdRequest)) { userIdRequest =>
      onSuccess(service.getUser(userIdRequest)) {
        case GetUserResponse.Found(user) =>
          complete(user)
        case GetUserResponse.NotFound(_) =>
          complete(StatusCodes.NotFound)
      }
    }

  private def createUser: Route =
    (post & entity(as[User])) { user =>
      onSuccess(service.createUser(CreateUserRequest(user))) { response =>
        complete(response.user)
      }
    }
}
