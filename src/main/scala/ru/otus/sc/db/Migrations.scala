package ru.otus.sc.db

import org.flywaydb.core.Flyway
import ru.otus.sc.Config

class Migrations(config: Config) {
  def applyMigrationsSync(): Unit =
    Flyway
      .configure()
      .dataSource(config.dbUrl, config.dbUserName, config.dbPassword)
      .load()
      .migrate()

}
