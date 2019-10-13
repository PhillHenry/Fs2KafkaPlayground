package uk.co.odinconsultants.fp.cats.fs2.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import fs2.kafka._

object ProducerMain  extends IOApp {

  import Settings._

  override def run(args: List[String]): IO[ExitCode] = {

    val pStream =
      producerStream[IO]
        .using(producerSettings)
        .flatMap { producer =>
          println(s"flatMap")
          val record = ProducerRecord("topic", "key", "value")
          val io = producer.produce(ProducerRecords.one(record, 0L))
          println(s"produced = $io")
          fs2.Stream.apply(io)
        }
    println("About to drain...")
    val result = pStream.compile.drain.as(ExitCode.Success)
    println("Finished draining.")
    result
  }
}
