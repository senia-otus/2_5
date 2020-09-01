package ru.otus.sc.greet.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.freespec.AnyFreeSpec
import ru.otus.sc.greet.model.{GreetRequest, GreetResponse}
import ru.otus.sc.greet.service.GreetingService
import org.scalatest.matchers.should.Matchers._

class GreetRouterSpec extends AnyFreeSpec with ScalatestRouteTest with MockFactory {

  "Methods tests" - {
    "route" in {
      val srv    = mock[GreetingService]
      val router = new GreetRouter(srv)

      (srv.greet _).expects(GreetRequest("a")).returns(GreetResponse("abc"))

      Get("/greet/a") ~> router.route ~> check {
        handled shouldBe true
        responseAs[String] shouldBe "abc"
        status shouldBe StatusCodes.OK
      }
    }
  }
}
