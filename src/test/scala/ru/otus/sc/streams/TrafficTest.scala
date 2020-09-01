package ru.otus.sc.streams

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source, UnzipWith, ZipWith}
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.time.{Seconds, Span}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class TrafficTest extends ScalaTestWithActorTestKit with AnyFreeSpecLike {
  private val log = LoggerFactory.getLogger(getClass)

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(100, Seconds)))

  "Single bus" - {
    "should transfer people in groups" in {
      case class Person(id: Int)

      case class Bus(passengers: Seq[Person])

      def transfer(bus: Bus): Unit = log.info(s"Arrived: $bus")

      // Source: [] ~>
      // Flow: ~> [] ~>
      // Sink: ~> []
      // RunnableGraph: []

      val src: Source[Person, NotUsed] = Source.unfold(0) { i =>
        if (i <= 100) Some((i + 1, Person(i)))
        else None
      }

      // persons ~> groupBy(10) ~> toBus ~> transfer ~> Sink?

      src
        .grouped(10)
        .map(Bus)
        .map(transfer)
        .runWith(Sink.ignore)
        .futureValue

    }

    "should transfer people in groups async" in {
      case class Person(id: Int)

      case class Bus(passengers: Seq[Person])

      def transfer(bus: Bus): Future[Unit] = Future.successful(log.info(s"Arrived: $bus"))

      Source(1 to 100)
        .map(Person)
        .grouped(10)
        .map(Bus)
        .mapAsync(2)(transfer)
        .runWith(Sink.ignore)
        .futureValue
    }

    "should now wait too long" in {
      case class Person(id: Int)

      case class Bus(passengers: Seq[Person])

      def transfer(bus: Bus): Future[Unit] = Future.successful(log.info(s"Arrived: $bus"))

      Source(1 to 100)
        .map(Person)
        .throttle(7, 1.second)
        .groupedWithin(10, 1.second)
        .map(Bus)
        .mapAsync(2)(transfer)
        .runWith(Sink.ignore)
        .futureValue

    }

    "should respect weight limit" in {
      case class Person(id: Int, weight: Int)

      case class Bus(passengers: Seq[Person])

      def transfer(bus: Bus): Future[Unit] = Future.successful(log.info(s"Arrived: $bus"))

      Source(1 to 100)
        .map(i => Person(i, 1 + i % 7))
        .throttle(8, 1.second)
        .groupedWeightedWithin(7, 1.second)(_.weight)
        .map(Bus)
        .mapAsync(2)(transfer)
        .runWith(Sink.ignore)
        .futureValue

    }
  }

  "Airplane and buss" - {
    "should transfer people to a single destination" in {
      case class Person(id: Int)

      case class Airplane(passengers: Seq[Person])
      case class Bus(passengers: Seq[Person])

      def transferByPlane(plane: Airplane): Future[Seq[Person]] =
        Future.successful {
          log.info(s"Airplane arrived: $plane")
          plane.passengers
        }

      def transferByBus(bus: Bus): Future[Seq[Person]] =
        Future.successful {
          log.info(s"Bus arrived: $bus")
          bus.passengers
        }

      Source(1 to 100)
        .map(Person)
        .grouped(35)
        .map(Airplane)
        .mapAsync(1)(transferByPlane)
        .mapConcat(identity)
        .grouped(10)
        .map(Bus)
        .mapAsync(2)(transferByBus)
        .runWith(Sink.ignore)
        .futureValue

    }

    "should transfer people reusing buses" in {
      sealed trait Destination
      case object Msk extends Destination
      case object Spb extends Destination

      case class Person(id: Int)

      case class Bus(id: Int, passengers: Seq[Person] = Seq.empty)

      def transfer(bus: Bus): Future[Bus] =
        pattern.after(Random.nextInt(100).millis) {
          Future.successful {
            log.info(s"Arrived: $bus")
            bus
          }
        }

      def goBack(bus: Bus): Future[Bus] =
        pattern.after(Random.nextInt(100).millis) {
          Future.successful {
            log.info(s"Got back: $bus")
            bus
          }
        }

      def flow =
        Flow.fromGraph(GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val busParking = Source(Seq(Bus(1), Bus(2), Bus(3)))

          val busIn = builder.add(Merge[Bus](2))

          val inStation =
            builder.add(ZipWith[Bus, Seq[Person], Bus]((b, ps) => b.copy(passengers = ps)))

          val travelToDestination = builder.add(Flow[Bus].mapAsyncUnordered(3)(transfer))

          val outStation = builder.add(
            UnzipWith[Bus, Bus, Seq[Person]](b => b.copy(passengers = Nil) -> b.passengers)
          )

          val travelBack = builder.add(Flow[Bus].mapAsyncUnordered(3)(goBack))

          // format: off
          busParking ~> busIn ~> inStation.in0
                                 inStation.out ~> travelToDestination ~> outStation.in
                        busIn <~                  travelBack <~          outStation.out0
          // format: on

          FlowShape(inStation.in1, outStation.out1)
        })

      Source(1 to 100)
        .map(Person)
        .grouped(10)
        .via(flow)
        .runWith(Sink.ignore)
        .futureValue
    }
  }

}
