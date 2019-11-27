package processes

import java.nio.file.Paths

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Clock, ConcurrentEffect, ContextShift, IO}
import io.circe.Json
import fs2.{Pipe, Stream}
import jawnfs2._
import org.typelevel.jawn.RawFacade
import cats.implicits._
import http.{EmojiStatus, Hashtag, Media, Status, Url}
import io.circe.syntax._
import org.http4s.implicits._
import org.http4s.circe._
import io.circe.generic.auto._

import scala.concurrent.duration.MILLISECONDS

trait TweetProcesses[F[_]] {

  private def readEmojiFile(source: String)(blocker: Blocker)(implicit cs: ContextShift[F], ce: ConcurrentEffect[F]) =
    fs2.io.file.readAll[F](Paths.get(source), blocker.blockingContext, 10000)


  private def getEmojiStream(source: String, decoder: Pipe[F, Json, Either[Throwable, Emoji]])(blocker: Blocker)(implicit cs: ContextShift[F], ce: ConcurrentEffect[F], facade: RawFacade[Json]) = {
    (readEmojiFile(source)(blocker).chunks.unwrapJsonArray through decoder)
      .filter(_.isRight)
      .map(_.right.get)
  }

  def getEmojiList(source: String, decoder: Pipe[F, Json, Either[Throwable, Emoji]])(blocker: Blocker)
                  (implicit cs: ContextShift[F], ce: ConcurrentEffect[F], facade: RawFacade[Json]) = {
    Stream.eval(getEmojiStream(source, decoder)(blocker)
      .filter(_.has_img_twitter)
      .compile.toList)
  }

  def updateEmoji(tweet: String, ref: Ref[IO, Reference]) =
    for {
      reference <- ref.get
      emojiList = reference.emojis.map(_.unified) filter tweet.contains
      _ <- if (emojiList.nonEmpty) ref.set(reference.copy(emojiNumber = reference.emojiNumber + 1)) else IO.unit
      _ <- emojiList.scanLeft(IO.unit) { (io, emoji) =>
        ref.get.flatMap { reference =>
          val emojis = reference.emojis
          val index = emojis.indexWhere(_.unified == emoji)
          val newNumberEmoji = emojis(index).copy(number = emojis(index).number + emoji.r.findAllIn(tweet).size)
          val newEmojis = emojis.updated(index, newNumberEmoji)
          ref.set(reference.copy(emojis = newEmojis))
        }
      }.sequence
    } yield reference


  def getTopEmoji(ref: Ref[IO, Reference]) =
    ref.get.map { reference =>
      reference.emojis.maxBy(_.number)
    }

  private def getEmojiNumber(ref: Ref[IO, Reference]) =
    ref.get.map(_.emojiNumber)

  def getEmojiPercentage(ref: Ref[IO, Reference]) =
    for {
      photoNumber <- getEmojiNumber(ref)
      tweetNumber <- getTweetNumber(ref)
      percentage: Double = photoNumber.toDouble / tweetNumber.toDouble * 100
    } yield percentage



  def increaseTweetNumber(ref: Ref[IO, Reference]) =
    ref.get.flatMap(reference => ref.set(reference.copy(tweetNumber = reference.tweetNumber + 1)))

  def getTweetNumber(ref: Ref[IO, Reference]) =
    ref.get.map(_.tweetNumber)

  private def updateHashtag(ref: Ref[IO, Reference])(hashtag: Hashtag) =
    ref.get.flatMap { reference =>
      val hashtags = reference.hashtags
      val oldNumber = hashtags.find(_._1 == hashtag.text).map(_._2).getOrElse(0)
      val newHashtags = hashtags.updated(hashtag.text, oldNumber + 1)
      ref.set(reference.copy(hashtags = newHashtags))
    }

  def updateHashtags(hashtagsOption: Option[List[Hashtag]], ref: Ref[IO, Reference]) =
    hashtagsOption.map { hashtags =>
      (hashtags map updateHashtag(ref)).sequence
    }.getOrElse(IO.unit)

  def getTopHashtag(ref: Ref[IO, Reference]) =
    ref.get.map { reference =>
      if (reference.hashtags.nonEmpty) reference.hashtags.maxBy(_._2)._1 else "No hashtags have been tweeted yet"
    }

  private def updateUrl(ref: Ref[IO, Reference])(url: Url) =
    ref.get.flatMap { reference =>
      val urls = reference.urls
      val oldNumber = urls.find(_._1 == url.expanded_url).map(_._2).getOrElse(0)
      val newUrls = urls.updated(url.expanded_url, oldNumber + 1)
      ref.set(reference.copy(urls = newUrls))
    }


  def updateUrls(urlOption: Option[List[Url]], ref: Ref[IO, Reference]) =
    for {
      reference <- ref.get
      _ <- if (urlOption.nonEmpty && urlOption.get.nonEmpty) ref.set(reference.copy(urlNumber = reference.urlNumber + 1)) else IO.unit
      _ <- urlOption.map { urls =>
        (urls map updateUrl(ref)).sequence
      }.getOrElse(IO.unit)
    } yield reference


  def getTopUrl(ref: Ref[IO, Reference]) =
    ref.get.map { reference =>
      if (reference.urls.nonEmpty) reference.urls.maxBy(_._2)._1 else "No urls have been tweeted yet"
    }

  private def getUrlNumber(ref: Ref[IO, Reference]) =
    ref.get.map(_.urlNumber)

  def getUrlPercentage(ref: Ref[IO, Reference]) =
    for {
      urlNumber <- getUrlNumber(ref)
      tweetNumber <- getTweetNumber(ref)
      percentage: Double = urlNumber.toDouble / tweetNumber.toDouble * 100

    } yield percentage


  def updatePhotos(mediaOption: Option[List[Media]], ref: Ref[IO, Reference]) =
    ref.get.flatMap { reference =>
      if (mediaOption.nonEmpty && mediaOption.get.exists(_.`type` == "photo")) ref.set(reference.copy(photoNumber = reference.photoNumber + 1)) else IO.unit

    }

  private def getPhotoNumber(ref: Ref[IO, Reference]) =
    ref.get.map(_.photoNumber)


  def getPhotoPercentage(ref: Ref[IO, Reference]) =
    for {
      photoNumber <- getPhotoNumber(ref)
      tweetNumber <- getTweetNumber(ref)
      percentage: Double = photoNumber.toDouble / tweetNumber.toDouble * 100
    } yield percentage

  def getRate(ref: Ref[IO, Reference])(implicit clock: Clock[IO]) ={
    for {
      time <- clock.monotonic(MILLISECONDS)
      reference <- ref.get
      startTime = reference.startTime
      numberOfTweets = reference.tweetNumber
      totalTime = time - startTime
      rate: Double = numberOfTweets.toDouble / totalTime.toDouble
      secondRate = rate * 1000
      minuteRate = secondRate * 60
      hourRate = minuteRate * 60
    } yield Rate(secondRate, minuteRate, hourRate)
  }

  def getStatus(ref: Ref[IO, Reference])(implicit clock: Clock[IO]) ={
    for{
      reference <- ref.get
      numberOfTweets <- getTweetNumber(ref)
      rate <- getRate(ref)
      topEmoji <- getTopEmoji(ref)
      emojiPercent <- getEmojiPercentage(ref)
      topHashtag <- getTopHashtag(ref)
      urlPercent <- getUrlPercentage(ref)
      photoPercent <- getPhotoPercentage(ref)
      topUrl <- getTopUrl(ref)
    } yield Status(numberOfTweets, rate.second, rate.minute, rate.hour, EmojiStatus(topEmoji.name, topEmoji.unified), emojiPercent, topHashtag, urlPercent, photoPercent, topUrl).asJson
  }
}

case class Emoji(name: String = "No Name", unified: String, has_img_twitter: Boolean, number: Int = 0)

case class Reference(emojis: List[Emoji], tweetNumber: Int, emojiNumber: Int, hashtags: Map[String, Int], urls: Map[String, Int], urlNumber: Int, photoNumber: Int, startTime: Long)

case class Rate(second: Double, minute: Double, hour: Double)
