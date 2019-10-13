package uk.co.odinconsultants.fp.cats.fs2.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import fs2.kafka._
import scala.concurrent.duration._

object ConsumerMain extends IOApp {

  import Settings._

  def processRecord(record: ConsumerRecord[String, String]): IO[(String, String)] =
    IO.pure(record.key -> record.value)

  override def run(args: List[String]): IO[ExitCode] = {

    val cStream =
      consumerStream[IO]
        .using(consumerSettings)
        .evalTap(_.subscribeTo(topicName))
        .flatMap(_.stream)
        .mapAsync(25)(commit)
        .through(produce(producerSettings))
        .map(passingThrough)
        .through(commitBatchWithin(500, 15.seconds))

    println("Draining stream")
    val result = cStream.compile.drain.as(ExitCode.Success)
    println("Done.")

    result
  }

  private def passingThrough(producerResult: ProducerResult[String, String, CommittableOffset[IO]]): CommittableOffset[IO] = {
    producerResult.passthrough
  }

  private def commit(committable: CommittableConsumerRecord[IO, String, String]): IO[ProducerRecords[String, String, CommittableOffset[IO]]] = {
    println(s"committable = $committable")
    val io: IO[(String, String)] = processRecord(committable.record)
    io.map { case (key, value) =>
      val record = ProducerRecord("topic", key, value)
      println(s"record = $record")
      ProducerRecords.one(record, committable.offset)
    }
  }
}