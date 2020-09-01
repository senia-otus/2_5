import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import cats.effect.{Resource, _}
import cats.implicits._
import doobie._
import doobie.hikari._
import doobie.util.transactor.Transactor
import ru.otus.sc.Config
import ru.otus.sc.db.Migrations
import ru.otus.sc.files.route.FilesRouter
import ru.otus.sc.files.service.{ClamAVConfig, ClamAVService, FileService}
import ru.otus.sc.greet.dao.impl.GreetingDaoImpl
import ru.otus.sc.greet.route.GreetRouter
import ru.otus.sc.greet.service.impl.GreetingServiceImpl
import ru.otus.sc.route.Router
import ru.otus.sc.user.dao.doobie.UserDaoDoobieImpl
import ru.otus.sc.user.route.UserRouter
import ru.otus.sc.user.service.impl.UserServiceImpl

import scala.io.StdIn

object DoobieMain {
  def createRoute(tr: Transactor[IO])(implicit system: ActorSystem): Router = {
    import system.dispatcher
    val greetRouter = new GreetRouter(new GreetingServiceImpl(new GreetingDaoImpl))

    val userDao = new UserDaoDoobieImpl(tr)

    val userService = new UserServiceImpl(userDao)

    val userRouter = new UserRouter(userService)

    new Router(
      greetRouter,
      userRouter,
      new FilesRouter(new FileService(new ClamAVService(ClamAVConfig.default)))
    )
  }

  def main(args: Array[String]): Unit = {

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContexts.synchronous)

    def makeBinding(tr: Transactor[IO])(implicit system: ActorSystem) = {

      Resource
        .make(
          IO.fromFuture(
            IO(
              Http()(system)
                .newServerAt("localhost", 8080)
                .bind(createRoute(tr).route)
            )
          )
        )(b => IO.fromFuture(IO(b.unbind())).map(_ => ()))

    }

    val config = Config.default

    val binding =
      for {
        ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
        be <- Blocker[IO] // our blocking EC
        xa <- HikariTransactor.newHikariTransactor[IO](
          "org.postgresql.Driver", // driver classname
          config.dbUrl,            // connect URL
          config.dbUserName,       // username
          config.dbPassword,       // password
          ce,                      // await connection here
          be                       // execute JDBC operations here
        )
        system <- Resource.make(IO(ActorSystem("system")))(s =>
          IO.fromFuture(IO(s.terminate())).map(_ => ())
        )
        binding <- makeBinding(xa)(system)
      } yield binding

    val app =
      binding
        .use { binding =>
          for {
            _ <- IO(println(s"Binding on ${binding.localAddress}"))
            _ <- IO(StdIn.readLine())
          } yield ()
        }

    val init = IO(new Migrations(config).applyMigrationsSync())

    (init *> app).unsafeRunSync()
  }
}
