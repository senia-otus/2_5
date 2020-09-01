package ru.otus.sc.files.service

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.Future

sealed trait ScanResult

object ScanResult {
  case object Ok                      extends ScanResult
  case class Found(details: String)   extends ScanResult
  case class Error(details: String)   extends ScanResult
  case class Unknown(details: String) extends ScanResult
}

case class ClamAVConfig(host: String, port: Int)
object ClamAVConfig {
  val default: ClamAVConfig = ClamAVConfig("localhost", 3310)
}

class ClamAVService(config: ClamAVConfig) {
  def scan(implicit system: ActorSystem): Sink[ByteString, Future[ScanResult]] = {
    val connection = Tcp().outgoingConnection(config.host, config.port)

    def chunkLength(length: Int): ByteString =
      ByteString.fromArray(ByteBuffer.allocate(4).putInt(length).array())

    val command     = Source.single(ByteString.fromString("zINSTREAM\u0000", StandardCharsets.US_ASCII))
    val termination = Source.single(chunkLength(0))

    val chunks: Flow[ByteString, ByteString, NotUsed] =
      Flow[ByteString]
        .filter(_.nonEmpty)
        .map(c => chunkLength(c.length).concat(c))

    val message = chunks.prepend(command).concat(termination)

    message
      .via(connection)
      .reduce(_.concat(_))
      .map(removeResponseTerminator)
      .map(_.decodeString(StandardCharsets.US_ASCII))
      .map(parseResponse)
      .toMat(Sink.last)(Keep.right)
  }

  private def parseResponse(message: String): ScanResult =
    if (message.endsWith(" OK")) ScanResult.Ok
    else if (message.endsWith(" FOUND")) ScanResult.Found(message)
    else if (message.contains("ERROR")) ScanResult.Error(message)
    else ScanResult.Unknown(message)

  private def removeResponseTerminator(byteString: ByteString): ByteString =
    if (byteString.last == 0) byteString.dropRight(1)
    else byteString
}
