package http

import org.http4s._
import org.http4s.client.{Client, oauth1}
import cats.effect._
import fs2.{Pipe, Stream}
import io.circe.{Decoder, Json}
import jawnfs2._
import io.circe.generic.auto._
import org.http4s.client.dsl.Http4sClientDsl
import org.typelevel.jawn.RawFacade

trait StreamingClient[F[_]] extends Http4sClientDsl[IO] {

  def signRequest(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F]) = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }

  private def streamJson(client: Client[F])(request: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F], facade: RawFacade[Json]): Stream[F, Json] =
    client.stream(request).flatMap(_.body.chunks.parseJsonStream)


  def streamTweets(client: Client[F], decoder: Pipe[F, Json, Either[Throwable, Tweet]])(request: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F], facade: RawFacade[Json]): Stream[F, Either[Throwable, Tweet]] =
    streamJson(client)(request) through decoder

}

case class Tweet(text: String, entities: Entities)

case class Entities(urls: Option[List[Url]], hashtags: Option[List[Hashtag]], media: Option[List[Media]])

case class Url(expanded_url: String)

case class Hashtag(text: String)

case class Media(`type`: String)