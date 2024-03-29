
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Clock, ExitCode, IO, IOApp}
import http.{Server, StreamingClient, Tweet}
import org.http4s.{Method, Request}
import org.http4s.client.blaze.BlazeClientBuilder
import fs2.{Pipe, Stream}
import cats.implicits._
import com.google.common.io.Resources
import com.typesafe.config.ConfigFactory
import config.TwitterConfig
import io.circe.{Decoder, Json}
import io.circe.generic.extras.auto._
import io.circe.config.syntax._
import io.circe.generic.extras.Configuration
import org.typelevel.jawn.RawFacade
import processes.{Emoji, Reference, TweetProcesses}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.MILLISECONDS

object Engine extends IOApp with StreamingClient[IO] with TweetProcesses[IO] with Server {

  implicit lazy val facade: RawFacade[Json] = io.circe.jawn.CirceSupportParser.facade
  implicit lazy val customConfig: Configuration = Configuration.default.withDefaults
  implicit lazy val clock: Clock[IO] = Clock.create[IO]

  def run(args: List[String]): IO[ExitCode] = {
    val stream = for {
      client <- BlazeClientBuilder[IO](global).stream
      emojis <- Stream.resource(Blocker[IO]) flatMap getEmojiList(Resources.getResource("emoji.json").getFile, decoder[IO, Emoji])
      startTime <- Stream.eval(clock.monotonic(MILLISECONDS))
      initialReference = Reference(emojis, 0, 0, Map(), Map(), 0, 0, startTime)
      ref <- Stream.eval(Ref[IO].of(initialReference))
      twitterStream = (Stream.eval(IO.fromEither(ConfigFactory.load.as[TwitterConfig])) flatMap request flatMap streamTweets(client, decoder[IO, Tweet])).attempt
      tweetStream = twitterStream.map(_.flatten).filter(_.isRight).map(_.right.get)
      fullProcess = tweetStream through process(ref)
      processTweets <- fullProcess concurrently serverStream(8888, ref)
    } yield processTweets

    stream.compile.drain.as(ExitCode.Success)
  }

  private def request(twitterConfig: TwitterConfig) = {
    import twitterConfig._
    val request: IO[Request[IO]] = uri.flatMap(Method.GET(_))
    Stream.eval(request flatMap signRequest(consumerKey, consumerSecret, accessToken, accessSecret))
  }

  private def decoder[F[_], A](implicit decode: Decoder[A], customConfig: Configuration): Pipe[F, Json, Either[Throwable, A]] = _.flatMap { json =>
    Stream.emit(decode(json.hcursor))
  }

  private def process(ref: Ref[IO, Reference]): Pipe[IO, Tweet, Unit] = _.mapAsyncUnordered(6000){ tweet =>
    for {
      _<- increaseTweetNumber(ref)
      _ <- updateEmoji(tweet.text, ref)
      _ <- updateHashtags(tweet.entities.hashtags, ref)
      _ <- updateUrls(tweet.entities.urls, ref)
      _ <- updatePhotos(tweet.entities.media, ref)
    } yield IO.unit
  }
}
