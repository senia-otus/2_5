package ru.otus.sc.user.dao.slick

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import ru.otus.sc.Config
import ru.otus.sc.db.Migrations
import ru.otus.sc.user.dao.{UserDao, UserDaoTest}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext.Implicits.global

class UserDaoSlickImplTest extends UserDaoTest("UserDaoDoobieImplTrest") with ForAllTestContainer {

  override val container: PostgreSQLContainer = PostgreSQLContainer()

  var db: Database = _

  override def afterStart(): Unit = {
    super.afterStart()
    new Migrations(Config(container.jdbcUrl, container.username, container.password))
      .applyMigrationsSync()

    db = Database.forURL(container.jdbcUrl, container.username, container.password)
  }

  override def beforeStop(): Unit = {
    db.close()
    super.beforeStop()
  }

  def createEmptyDao(): UserDao = {
    val dao = new UserDaoSlickImpl(db)
    dao.deleteAll().futureValue
    dao
  }
}
