package ru.otus.sc.user.service.impl

import java.util.UUID

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers._
import ru.otus.sc.user.dao.UserDao
import ru.otus.sc.user.model.{
  CreateUserRequest,
  CreateUserResponse,
  DeleteUserRequest,
  DeleteUserResponse,
  FindUsersRequest,
  FindUsersResponse,
  GetUserRequest,
  GetUserResponse,
  Role,
  UpdateUserRequest,
  UpdateUserResponse,
  User
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserServiceImplTest extends AnyFreeSpec with MockFactory with ScalaFutures {

  val user1 = User(Some(UUID.randomUUID()), "SomeName1", "SomeLastName1", 1, Set(Role.Manager))
  val user2 = User(Some(UUID.randomUUID()), "SomeName2", "SomeLastName2", 2, Set(Role.Admin))

  "UserServiceTest tests" - {
    "createUser" - {
      "should create user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)

        (dao.createUser _).expects(user1).returns(Future.successful(user2))

        srv.createUser(CreateUserRequest(user1)).futureValue shouldBe CreateUserResponse(user2)
      }
    }

    "getUser" - {
      "should return user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)
        val id  = UUID.randomUUID()

        (dao.getUser _).expects(id).returns(Future.successful(Some(user1)))

        srv.getUser(GetUserRequest(id)).futureValue shouldBe GetUserResponse.Found(user1)
      }

      "should return NotFound on unknown user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)
        val id  = UUID.randomUUID()

        (dao.getUser _).expects(id).returns(Future.successful(None))

        srv.getUser(GetUserRequest(id)).futureValue shouldBe GetUserResponse.NotFound(id)
      }
    }

    "updateUser" - {
      "should update existing user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)

        (dao.updateUser _).expects(user1).returns(Future.successful(Some(user2)))

        srv.updateUser(UpdateUserRequest(user1)).futureValue shouldBe UpdateUserResponse.Updated(
          user2
        )
      }

      "should return NotFound on unknown user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)

        (dao.updateUser _).expects(user1).returns(Future.successful(None))

        srv.updateUser(UpdateUserRequest(user1)).futureValue shouldBe UpdateUserResponse.NotFound(
          user1.id.get
        )
      }

      "should return CantUpdateUserWithoutId on user without id" in {
        val dao  = mock[UserDao]
        val srv  = new UserServiceImpl(dao)
        val user = user1.copy(id = None)

        srv
          .updateUser(UpdateUserRequest(user))
          .futureValue shouldBe UpdateUserResponse.CantUpdateUserWithoutId
      }
    }

    "deleteUser" - {
      "should delete user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)
        val id  = UUID.randomUUID()

        (dao.deleteUser _).expects(id).returns(Future.successful(Some(user1)))

        srv.deleteUser(DeleteUserRequest(id)).futureValue shouldBe DeleteUserResponse.Deleted(user1)
      }

      "should return NotFound on unknown user" in {
        val dao = mock[UserDao]
        val srv = new UserServiceImpl(dao)
        val id  = UUID.randomUUID()

        (dao.deleteUser _).expects(id).returns(Future.successful(None))

        srv.deleteUser(DeleteUserRequest(id)).futureValue shouldBe DeleteUserResponse.NotFound(id)
      }
    }

    "findUsers" - {
      "by last name" - {
        "should return empty list" in {
          val dao      = mock[UserDao]
          val srv      = new UserServiceImpl(dao)
          val lastName = "abc"

          (dao.findByLastName _).expects(lastName).returns(Future.successful(Seq.empty))

          srv
            .findUsers(FindUsersRequest.ByLastName(lastName))
            .futureValue shouldBe FindUsersResponse.Result(
            Seq.empty
          )
        }

        "should return non-empty list" in {
          val dao      = mock[UserDao]
          val srv      = new UserServiceImpl(dao)
          val lastName = "abc"

          (dao.findByLastName _).expects(lastName).returns(Future.successful(Seq(user1, user2)))

          srv
            .findUsers(FindUsersRequest.ByLastName(lastName))
            .futureValue shouldBe FindUsersResponse.Result(
            Seq(user1, user2)
          )
        }
      }
    }
  }
}
