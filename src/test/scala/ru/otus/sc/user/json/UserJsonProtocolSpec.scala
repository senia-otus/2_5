package ru.otus.sc.user.json

import org.scalatest.freespec.AnyFreeSpec
import UserJsonProtocol._
import org.scalacheck.ScalacheckShapeless._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsSuccess, Json}
import ru.otus.sc.user.model.User
import org.scalatest.matchers.should.Matchers._

class UserJsonProtocolSpec extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {

  "Methods tests" - {
    "userFormat" in {
      forAll { user: User =>
        Json.fromJson[User](Json.toJson(user)) shouldBe (JsSuccess(user))
      }
    }
  }
}
