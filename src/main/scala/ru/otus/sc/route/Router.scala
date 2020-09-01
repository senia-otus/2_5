package ru.otus.sc.route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ru.otus.sc.files.route.FilesRouter

class Router(greetRouter: BaseRouter, userRouter: BaseRouter, filesRouter: FilesRouter)
    extends BaseRouter {
  def route: Route =
    pathPrefix("api" / "v1") {
      concat(
        greetRouter.route,
        userRouter.route,
        filesRouter.route
      )
    }
}
