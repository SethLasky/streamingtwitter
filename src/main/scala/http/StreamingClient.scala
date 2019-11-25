package http

import org.http4s._
import org.http4s.client.{Client, oauth1}
import cats.effect._
import fs2.{Pipe, Stream}
import io.circe.{Decoder, Json}
import jawnfs2._
import io.circe.generic.auto._
import org.http4s.client.dsl.Http4sClientDsl

trait StreamingClient[F[_]] extends Http4sClientDsl[IO] {

  implicit val facade = io.circe.jawn.CirceSupportParser.facade

  def signRequest(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(req: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F]) = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }

  private def streamJson(client: Client[F])(request: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F]): Stream[F, Json] =
    client.stream(request).flatMap(_.body.chunks.parseJsonStream)

  private def decoder(implicit decode: Decoder[Tweet]): Pipe[F, Json, Either[Throwable, Tweet]] = _.flatMap { json =>
    Stream.emit(decode(json.hcursor))
  }

  def streamTweets(client: Client[F])(request: Request[F])(implicit ce: ConcurrentEffect[F], cs: ContextShift[F]): Stream[F, Either[Throwable, Tweet]] =
    streamJson(client)(request) through decoder
}

case class Tweet(text: String, entities: Entities)

case class Entities(urls: Option[List[Url]], media: Option[List[Media]])

case class Url(expanded_url: String)

case class Media(`type`: String)