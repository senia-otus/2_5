package ru.otus.sc.greet.route

import akka.http.scaladsl.server.{Directive, Route}
import akka.http.scaladsl.server.Directives._
import ru.otus.sc.greet.model.GreetRequest
import ru.otus.sc.greet.service.GreetingService
import ru.otus.sc.route.BaseRouter

//GET http://localhost:8080/greet/MyName?isHuman=true
class GreetRouter(service: GreetingService) extends BaseRouter {
  // get: Directive0: ( => Route) => Route
  // path(Segment): Directive1[String]: (String => Route) => Route

  private val isHumanParam = parameter("isHuman".as[Boolean].?(true))

  private val PersonName = Segment

  def route: Route =
    (get & path("greet" / PersonName) & isHumanParam) { (name, isHuman) =>
      val resp = service.greet(GreetRequest(name = name, isHuman = isHuman))
      complete(resp.greeting)
    }

}
