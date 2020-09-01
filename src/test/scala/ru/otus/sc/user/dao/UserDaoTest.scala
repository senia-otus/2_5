package ru.otus.sc.user.dao

import java.util.UUID

import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ru.otus.sc.user.model.{Role, User}

/**
  * Abstract test class that should bw inherited by tests for any UserDao implementation
  */
abstract class UserDaoTest(name: String)
    extends AnyFreeSpec
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures {
  def createEmptyDao(): UserDao

  implicit val genRole: Gen[Role]             = Gen.oneOf(Role.Admin, Role.Manager, Role.Reader)
  implicit val arbitraryRole: Arbitrary[Role] = Arbitrary(genRole)

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)))

  /**
    * https://postgrespro.ru/list/thread-id/1932587
    */
  implicit lazy val arbString: Arbitrary[String] =
    Arbitrary(arbitrary[List[Char]] map (_.filter(_ != 0).mkString))

  implicit val genUser: Gen[User] = for {
    id        <- Gen.option(Gen.uuid)
    firstName <- arbitrary[String]
    lastName  <- arbitrary[String]
    age       <- arbitrary[Int]
    roles     <- arbitrary[Seq[Role]]
  } yield User(id = id, firstName = firstName, lastName = lastName, age = age, roles = roles.toSet)

  implicit val arbitraryUser: Arbitrary[User] = Arbitrary(genUser)

  name - {
    "createUser" - {
      "create any number of users" in {
        forAll { (users: Seq[User], user: User) =>
          val dao = createEmptyDao()
          users.foreach(dao.createUser(_).futureValue)

          val createdUser = dao.createUser(user).futureValue
          createdUser.id shouldNot be(user.id)
          createdUser.id shouldNot be(None)

          createdUser shouldBe user.copy(id = createdUser.id)
        }
      }
    }

    "getUser" - {
      "get unknown user" in {
        forAll { (users: Seq[User], userId: UUID) =>
          val dao = createEmptyDao()
          users.foreach(dao.createUser(_).futureValue)

          dao.getUser(userId).futureValue shouldBe None
        }
      }

      "get known user" in {
        forAll { (users1: Seq[User], user: User, users2: Seq[User]) =>
          val dao = createEmptyDao()
          users1.foreach(dao.createUser(_).futureValue)
          val createdUser = dao.createUser(user).futureValue
          users2.foreach(dao.createUser(_).futureValue)

          dao.getUser(createdUser.id.get).futureValue shouldBe Some(createdUser)
        }
      }
    }

    "updateUser" - {
      "update unknown user - keep all users the same" in {
        forAll { (users: Seq[User], user: User) =>
          val dao          = createEmptyDao()
          val createdUsers = users.map(dao.createUser(_).futureValue)

          dao.updateUser(user).futureValue shouldBe None

          createdUsers.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }
        }
      }

      "update known user - keep other users the same" in {
        forAll { (users1: Seq[User], user1: User, user2: User, users2: Seq[User]) =>
          val dao           = createEmptyDao()
          val createdUsers1 = users1.map(dao.createUser(_).futureValue)
          val createdUser   = dao.createUser(user1).futureValue
          val createdUsers2 = users2.map(dao.createUser(_).futureValue)

          val toUpdate = user2.copy(id = createdUser.id)
          dao.updateUser(toUpdate).futureValue shouldBe Some(toUpdate)
          dao.getUser(toUpdate.id.get).futureValue shouldBe Some(toUpdate)

          createdUsers1.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }

          createdUsers2.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }
        }
      }
    }

    "deleteUser" - {
      "delete unknown user - keep all users the same" in {
        forAll { (users: Seq[User], userId: UUID) =>
          val dao          = createEmptyDao()
          val createdUsers = users.map(dao.createUser(_).futureValue)

          dao.deleteUser(userId).futureValue shouldBe None

          createdUsers.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }
        }
      }

      "delete known user - keep other users the same" in {
        forAll { (users1: Seq[User], user1: User, users2: Seq[User]) =>
          val dao           = createEmptyDao()
          val createdUsers1 = users1.map(dao.createUser(_).futureValue)
          val createdUser   = dao.createUser(user1).futureValue
          val createdUsers2 = users2.map(dao.createUser(_).futureValue)

          dao.getUser(createdUser.id.get).futureValue shouldBe Some(createdUser)
          dao.deleteUser(createdUser.id.get).futureValue shouldBe Some(createdUser)
          dao.getUser(createdUser.id.get).futureValue shouldBe None

          createdUsers1.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }

          createdUsers2.foreach { u =>
            dao.getUser(u.id.get).futureValue shouldBe Some(u)
          }
        }
      }

    }

    "findByLastName" in {
      forAll { (users1: Seq[User], lastName: String, users2: Seq[User]) =>
        val dao               = createEmptyDao()
        val withOtherLastName = users1.filterNot(_.lastName == lastName)
        val withLastName      = users2.map(_.copy(lastName = lastName))

        withOtherLastName.foreach(dao.createUser(_).futureValue)
        val createdWithLasName = withLastName.map(dao.createUser(_).futureValue)

        dao.findByLastName(lastName).futureValue.toSet shouldBe createdWithLasName.toSet
      }
    }

    "findAll" in {
      forAll { users: Seq[User] =>
        val dao          = createEmptyDao()
        val createdUsers = users.map(dao.createUser(_).futureValue)

        dao.findAll().futureValue.toSet shouldBe createdUsers.toSet
      }
    }
  }
}
