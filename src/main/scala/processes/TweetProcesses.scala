package processes

import java.nio.file.Paths

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO}
import io.circe.Json
import fs2.{Pipe, Stream}
import jawnfs2._
import org.typelevel.jawn.RawFacade
import cats.implicits._

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

  def increaseTweetNumber(ref: Ref[IO, Reference]) =
    ref.get.flatMap(reference => ref.set(reference.copy(tweetNumber = reference.tweetNumber + 1)))

  def getTweetNumber(ref: Ref[IO, Reference]) =
    ref.get.map(_.tweetNumber)
}

case class Emoji(name: String = "No Name", unified: String, has_img_twitter: Boolean, number: Int = 0)

case class Reference(emojis: List[Emoji], tweetNumber: Int, emojiNumber: Int)