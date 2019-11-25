package util

import cats.effect.{ContextShift, IO, Timer}
import fs2.{Pipe, Stream}
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Json}
import org.typelevel.jawn.RawFacade

import scala.concurrent.ExecutionContext

trait TestUtils {

  lazy implicit val ctxShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  lazy implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  lazy implicit val facade: RawFacade[Json] = io.circe.jawn.CirceSupportParser.facade
  implicit lazy val customConfig: Configuration = Configuration.default.withDefaults

  def decoder[F[_], A](implicit decode: Decoder[A]): Pipe[F, Json, Either[Throwable, A]] = _.flatMap { json =>
    Stream.emit(decode(json.hcursor))
  }
}
