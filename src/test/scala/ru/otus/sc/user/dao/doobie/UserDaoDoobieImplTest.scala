package ru.otus.sc.user.dao.doobie

import cats.effect._
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import doobie._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor.Aux
import ru.otus.sc.Config
import ru.otus.sc.db.Migrations
import ru.otus.sc.user.dao.{UserDao, UserDaoTest}

import scala.concurrent.ExecutionContext.Implicits.global

class UserDaoDoobieImplTest extends UserDaoTest("UserDaoDoobieImplTrest") with ForAllTestContainer {

  override val container: PostgreSQLContainer = PostgreSQLContainer()

  override def afterStart(): Unit = {
    super.afterStart()
    new Migrations(Config(container.jdbcUrl, container.username, container.password))
      .applyMigrationsSync()
  }

  def createEmptyDao(): UserDao = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContexts.synchronous)

    val xa: Aux[IO, Unit] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",                                    // driver classname
      container.jdbcUrl,                                          // connect URL (driver-specific)
      container.username,                                         // user
      container.password,                                         // password
      Blocker.liftExecutionContext(ExecutionContexts.synchronous) // just for testing
    )

    val dao = new UserDaoDoobieImpl(xa)

    dao.deleteAll().futureValue
    dao
  }
}
