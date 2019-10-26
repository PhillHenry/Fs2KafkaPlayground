package uk.co.odinconsultants.fp.cats.fs2.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import fs2.Stream
import fs2.kafka._

import scala.concurrent.duration._

object ConsumerMain extends IOApp {

  import Settings._

  def processRecord(record: ConsumerRecord[String, String]): IO[(String, String)] =
    IO.pure(record.key -> record.value)

  type MyKafkaConsumer = KafkaConsumer[IO, String, String]

  val subscribeFn: MyKafkaConsumer => IO[Unit] = _.subscribeTo(topicName)

  val toStreamFn: MyKafkaConsumer => Stream[IO, CommittableConsumerRecord[IO, String, String]] = _.stream

  val commitFn: CommittableConsumerRecord[IO, String, String] => IO[ProducerRecords[String, String, CommittableOffset[IO]]] = { committable =>
    println(s"committable = $committable")
    val io: IO[(String, String)] = processRecord(committable.record)
    io.map { case (key, value) =>
      val record = ProducerRecord("topic", key, value)
      println(s"record = $record")
      ProducerRecords.one(record, committable.offset)
    }
  }

  val passingThroughFn: ProducerResult[String, String, CommittableOffset[IO]] => CommittableOffset[IO] = _.passthrough

  override def run(args: List[String]): IO[ExitCode] = {

    val cStream =
      consumerStream[IO]
        .using(consumerSettings)
        .evalTap(subscribeFn)
        .flatMap(toStreamFn)
        .mapAsync(25)(commitFn)
        .through(produce(producerSettings))
        .map(passingThroughFn)
        .through(commitBatchWithin(500, 15.seconds))

    println("Draining stream")
    val result = cStream.compile.drain.as(ExitCode.Success)
    println("Done.")

    result
  }

}