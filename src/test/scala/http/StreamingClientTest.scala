package http

import cats.effect.{ContextShift, IO, Timer}
import com.typesafe.config.ConfigFactory
import config.TwitterConfig
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{Method, Request, Uri}
import org.http4s.client.dsl.Http4sClientDsl
import org.scalatest.{Matchers, WordSpecLike}
import fs2.Stream
import io.circe.config.syntax._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class StreamingClientTest extends WordSpecLike with Matchers with StreamingClient[IO] with Http4sClientDsl[IO] {

  def request(twitterConfig: TwitterConfig)(implicit cs: ContextShift[IO]) = {
    import twitterConfig._
    val request: IO[Request[IO]] = uri.flatMap(Method.GET(_))
    Stream.eval(request flatMap signRequest(consumerKey, consumerSecret, accessToken, accessSecret))
  }

  "StreamingClient" must {
    lazy implicit val ctxShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    lazy implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
    "create a signed request" in {
      val key = "key"
      val request: IO[Request[IO]] = Method.GET(Uri.uri("request"))
      request.unsafeRunSync().headers.isEmpty shouldBe true
      val signedRequest = request flatMap signRequest(key, key, key, key)
      signedRequest.unsafeRunSync().headers.size shouldBe 1
    }

    "constantly stream either a Right(tweet) or a Left(nontweet)" in {
      val stream = for {
        client <- BlazeClientBuilder[IO](global).stream
        tweets <- Stream.eval(IO.fromEither(ConfigFactory.load.as[TwitterConfig])) flatMap request flatMap streamTweets(client)
      } yield tweets

      val list = stream.take(100).compile.toList.unsafeRunSync()
      list.exists(_.isRight) shouldBe true
      list.exists(_.isLeft) shouldBe true
    }
  }
}