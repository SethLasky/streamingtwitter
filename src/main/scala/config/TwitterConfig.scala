package config

import cats.effect.IO
import org.http4s.{ParseResult, Uri}

case class TwitterConfig(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String, url: String){
  val uri = IO.fromEither(Uri.fromString(url))
}
