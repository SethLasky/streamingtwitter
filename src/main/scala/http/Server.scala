package http

import cats.effect.concurrent.Ref
import cats.effect.{Clock, ConcurrentEffect, IO, Timer}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import processes.{Reference, TweetProcesses}


trait Server extends Http4sDsl[IO] with TweetProcesses[IO] {
  def serverStream(port: Int, ref: Ref[IO, Reference])(implicit effect: ConcurrentEffect[IO], timer: Timer[IO])  = BlazeServerBuilder[IO].bindHttp(port, host = "0.0.0.0").withHttpApp(router(ref)).serve

  private def router(ref: Ref[IO, Reference])(implicit clock: Clock[IO]) = Router("/" -> getResponse(ref)).orNotFound

  private def getResponse(ref: Ref[IO, Reference])(implicit clock: Clock[IO]) = HttpRoutes.of[IO]{
    case _ => Ok(getStatus(ref))
  }
}

case class Status(numberOfTweets: Int, tweetsPerSecond: Double, tweetsPerMinute: Double, tweetsPerHour: Double, topEmoji: EmojiStatus, emojiPercent: Double, topHashtag: String, urlPercent: Double, photoPercent: Double, topUrl: String)

case class EmojiStatus(name: String, emoji: String)


