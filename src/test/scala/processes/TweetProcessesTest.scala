package processes

import java.io.File

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, IO}
import fs2.Stream
import org.http4s.client.dsl.Http4sClientDsl
import org.scalatest.{Matchers, WordSpecLike}
import util.TestUtils

import io.circe.generic.extras.auto._

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
        refAfter <- ref.get

      } yield refAfter

      val reference = referenceIO.unsafeRunSync()
      reference.emojiNumber shouldBe 1
      reference.emojis.head.number shouldBe 1
      reference.emojis.last.number shouldBe 3
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

  }
  val emojis = List(Emoji("one", "〰", has_img_twitter = true), Emoji("two", "〽", has_img_twitter = true))

  def buildReference ={
    Ref[IO].of(Reference(emojis, 0, 0))
  }
}