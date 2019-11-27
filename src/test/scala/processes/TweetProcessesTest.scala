package processes

import java.io.File

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, IO}
import fs2.Stream
import http.{Hashtag, Media, Url}
import org.http4s.client.dsl.Http4sClientDsl
import org.scalatest.{Matchers, WordSpecLike}
import util.TestUtils
import io.circe.generic.extras.auto._
import scala.concurrent.duration._

class TweetProcessesTest extends WordSpecLike with Matchers with TweetProcesses[IO] with Http4sClientDsl[IO] with TestUtils {

  "TweetProcesses" must {
    "be able to read in a list of emojis from a file" in {
      val emojiStream = Stream.resource(Blocker[IO]) flatMap getEmojiList(getClass.getResource("/emoji.json").getPath, decoder[IO, Emoji])
      emojiStream.compile.toList.unsafeRunSync().flatten should not be empty
    }

    "update a reference of emojis and their numbers as well as total number of tweets with emojis in them" in {
      val referenceIO = for {
        ref <- buildReference
        _ <- updateEmoji("Things 〰 and other things 〽 〽 〽", ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        percentage <- getEmojiPercentage(ref)
        refAfter <- ref.get

      } yield (refAfter, percentage)

      val (reference, percentage) = referenceIO.unsafeRunSync()
      reference.emojiNumber shouldBe 1
      reference.emojis.head.number shouldBe 1
      reference.emojis.last.number shouldBe 3
      percentage shouldBe 25.0
    }

    "get the top emoji" in {
      val topIO = for {
        ref <- buildReference
        _ <- updateEmoji("Things 〰 and other things 〽 〽 〽", ref)
        top <- getTopEmoji(ref)

      } yield top
      topIO.unsafeRunSync() shouldBe emojis.last.copy(number = 3)
    }

    "increase the tweet number and get the tweet number" in {
      val numberIO = for {
        ref <- buildReference
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        number <- getTweetNumber(ref)

      } yield number
      numberIO.unsafeRunSync() shouldBe 2
    }

    "update the reference of hashtags and get the top hashtag" in {

      val hashtagIO = for {
        ref <- buildReference
        < <- updateHashtags(None, ref)
        _ <- updateHashtags(Some(List(Hashtag("ok"), Hashtag("great"))), ref)
        _ <- updateHashtags(Some(List(Hashtag("ok"), Hashtag("ok"))), ref)
        hashtag <- getTopHashtag(ref)

      } yield hashtag
      hashtagIO.unsafeRunSync() shouldBe "ok"
    }

    "update the reference of urls and get the top url and the percentage of urls" in {
      val urlIO = for {
        ref <- buildReference
        _ <- updateUrls(None, ref)
        _ <- updateUrls(Some(List(Url("url1"), Url("url2"))), ref)
        _ <- updateUrls(Some(List(Url("url1"), Url("url1"))), ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        url <- getTopUrl(ref)
        percentage <- getUrlPercentage(ref)

      } yield (url, percentage)
      val (url, percentage) = urlIO.unsafeRunSync()
      url shouldBe "url1"
      percentage shouldBe 50.0
    }

    "update the reference of photos and get the percentage of photos" in {
      val photoIO = for {
        ref <- buildReference
        _ <- updatePhotos(None, ref)
        _ <- updatePhotos(Some(List(Media("photo"), Media("somethingElse"))), ref)
        _ <- updatePhotos(Some(List(Media("photo"), Media("photo"))), ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        percentage <- getPhotoPercentage(ref)

      } yield percentage
      photoIO.unsafeRunSync() shouldBe 50.0
    }
    "get the rate of tweets at any given time" in {
      val rateIO = for {
        ref <- buildReference
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- increaseTweetNumber(ref)
        _ <- IO.sleep(5 seconds)
        rate <- getRate(ref)
      } yield rate

      rateIO.unsafeRunSync().second should be < 1.0
    }
  }


  val emojis = List(Emoji("one", "〰", has_img_twitter = true), Emoji("two", "〽", has_img_twitter = true))

  def buildReference ={
    for{
      startTime <- clock.monotonic(MILLISECONDS)
      ref <- Ref[IO].of(Reference(emojis, 0, 0, Map(), Map(), 0, 0, startTime))
    } yield ref
  }
}