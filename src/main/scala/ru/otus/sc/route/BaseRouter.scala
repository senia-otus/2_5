package ru.otus.sc.route

import akka.http.scaladsl.server.Route

trait BaseRouter {
  def route: Route
}
