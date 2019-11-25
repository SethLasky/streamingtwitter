
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import http.{StreamingClient, Tweet}
import org.http4s.{Method, Request}
import org.http4s.client.blaze.BlazeClientBuilder
import fs2.{Pipe, Stream}
import cats.implicits._
import com.typesafe.config.ConfigFactory
import config.TwitterConfig
import io.circe.{Decoder, Json}
import io.circe.generic.extras.auto._
import io.circe.config.syntax._
import io.circe.generic.extras.Configuration
import org.typelevel.jawn.RawFacade
import processes.{Emoji, Reference, TweetProcesses}

import scala.concurrent.ExecutionContext.global

object Engine extends IOApp with StreamingClient[IO] with TweetProcesses[IO] {

  implicit lazy val facade: RawFacade[Json] = io.circe.jawn.CirceSupportParser.facade
  implicit lazy val customConfig: Configuration = Configuration.default.withDefaults

  def run(args: List[String]): IO[ExitCode] = {
    val stream = for {
      client <- BlazeClientBuilder[IO](global).stream
      emojis <- Stream.resource(Blocker[IO]) flatMap getEmojiList(getClass.getResource("/emoji.json").getPath, decoder[IO, Emoji])
      ref <- Stream.eval(Ref[IO].of(Reference(emojis, 0, 0)))
      twitterStream = (Stream.eval(IO.fromEither(ConfigFactory.load.as[TwitterConfig])) flatMap request flatMap streamTweets(client, decoder[IO, Tweet])).attempt
      tweetStream = twitterStream.map(_.flatten).filter(_.isRight).map(_.right.get)
      processTweets <- tweetStream through process(ref)

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
    } yield IO.unit


  }
}
