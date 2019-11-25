import cats.effect.{ExitCode, IO, IOApp}
import http.StreamingClient
import org.http4s.{Method, Request}
import org.http4s.client.blaze.BlazeClientBuilder
import fs2.Stream
import cats.implicits._
import config.TwitterConfig
import com.typesafe.config.ConfigFactory
import io.circe.config.syntax._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.global

object Engine extends IOApp with StreamingClient[IO] {

  def run(args: List[String]): IO[ExitCode] = {
    val stream = for {
      client <- BlazeClientBuilder[IO](global).stream

      twitterStream = (Stream.eval(IO.fromEither(ConfigFactory.load.as[TwitterConfig])) flatMap request flatMap streamTweets(client)).attempt
      tweetStream = twitterStream.map(_.flatten).filter(_.isRight)

    } yield client

    stream.compile.drain.as(ExitCode.Success)
  }

  def request(twitterConfig: TwitterConfig) = {
    import twitterConfig._
    val request: IO[Request[IO]] = uri.flatMap(Method.GET(_))
    Stream.eval(request flatMap signRequest(consumerKey, consumerSecret, accessToken, accessSecret))
  }
}
