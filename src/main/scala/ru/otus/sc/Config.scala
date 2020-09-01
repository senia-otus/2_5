package ru.otus.sc

case class Config(
    dbUrl: String,
    dbUserName: String,
    dbPassword: String
)

object Config {
  val default: Config = Config(
    dbUrl = "jdbc:postgresql://localhost:5432/otusdb",
    dbUserName = "tmpuser",
    dbPassword = "tmppwd"
  )
}
