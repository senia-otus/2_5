import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import ru.otus.sc.Config
import ru.otus.sc.files.route.FilesRouter
import ru.otus.sc.files.service.{ClamAVConfig, ClamAVService, FileService}
import ru.otus.sc.greet.dao.impl.GreetingDaoImpl
import ru.otus.sc.greet.route.GreetRouter
import ru.otus.sc.greet.service.impl.GreetingServiceImpl
import ru.otus.sc.route.Router
import ru.otus.sc.user.dao.slick.UserDaoSlickImpl
import ru.otus.sc.user.route.UserRouter
import ru.otus.sc.user.service.impl.UserServiceImpl
import slick.jdbc.JdbcBackend.Database

import scala.io.StdIn
import scala.util.Using

object SlickMain {
  def createRoute(db: Database)(implicit system: ActorSystem): Router = {
    import system.dispatcher
    val greetRouter = new GreetRouter(new GreetingServiceImpl(new GreetingDaoImpl))

    val userDao = new UserDaoSlickImpl(db)

    val userService = new UserServiceImpl(userDao)

    val userRouter = new UserRouter(userService)

    new Router(
      greetRouter,
      userRouter,
      new FilesRouter(new FileService(new ClamAVService(ClamAVConfig.default)))
    )
  }

  def main(args: Array[String]): Unit = {

    val config = Config.default

    implicit val system: ActorSystem = ActorSystem("system")
    import system.dispatcher

    Using.resource(Database.forURL(config.dbUrl, config.dbUserName, config.dbPassword)) { db =>
      val binding = Http().newServerAt("localhost", 8080).bind(createRoute(db).route)

      binding.foreach(b => println(s"Binding on ${b.localAddress}"))

      StdIn.readLine()

      binding.map(_.unbind()).onComplete(_ -> system.terminate())
    }
  }
}
