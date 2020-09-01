package ru.otus.sc.files.service

import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.{IOResult, SinkShape}
import akka.stream.scaladsl.{Broadcast, FileIO, GraphDSL, Keep, Sink, Source}
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class FileService(clamav: ClamAVService)(implicit system: ActorSystem) {
  private val log = LoggerFactory.getLogger(getClass)

  import system.dispatcher

  private val clamavSink: Sink[ByteString, Future[ScanResult]] = clamav.scan

  def upload(name: String, data: Source[ByteString, _]): Future[Unit] = {
    val dir = Path.of("files")
    Files.createDirectories(dir)

    val fileName = dir.resolve(name)
    val toFile   = FileIO.toPath(fileName)

    def onScanResult(result: ScanResult): Unit =
      result match {
        case ScanResult.Ok => ()
        case _ =>
          Files.delete(fileName)
          log.warn(s"Failed scan result: $result")
          throw new RuntimeException(s"Scan failed: $result")
      }

    val combined: Sink[ByteString, Future[IOResult]] =
      Sink.fromGraph(
        GraphDSL.create(clamavSink, toFile)((r1, r2) => r1.map(onScanResult).flatMap(_ => r2)) {
          implicit builder => (s1, s2) =>
            import GraphDSL.Implicits._
            val broadcast = builder.add(Broadcast[ByteString](2))
            broadcast ~> s1
            broadcast ~> s2

            SinkShape(broadcast.in)
        }
      )

    data.runWith(combined).map(_ => ())
  }
}
