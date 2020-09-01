package ru.otus.sc.files.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ru.otus.sc.files.service.FileService
import ru.otus.sc.route.BaseRouter

class FilesRouter(fileService: FileService) extends BaseRouter {
  override def route: Route =
    (path("file") & post & fileUpload("file")) {
      case (info, data) =>
        onSuccess(fileService.upload(info.fileName, data)) {
          complete(StatusCodes.OK)
        }
    }
}
