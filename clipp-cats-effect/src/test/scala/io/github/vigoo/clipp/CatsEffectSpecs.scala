package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.effect._
import io.github.vigoo.clipp.catseffect._
import io.github.vigoo.clipp.errors.{CustomError, ParserError}
import io.github.vigoo.clipp.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class CatsEffectSpecs extends Specification {

  "Cats Effect interface" should {
    "successfully parse" in {
      val test = {
        val spec = flag("Test", 'x')
        Clipp.parseOrFail[Boolean](List("-x"), spec)
      }

      test.unsafeRunSync() === true
    }

    "fail on bad spec" in {
      val test = {
        val spec = flag("Test", 'x')
        Clipp.parseOrFail[Boolean](List("x"), spec)
          .map(Right.apply[Throwable, Boolean])
          .handleErrorWith(error => IO.pure(Left.apply[Throwable, Boolean](error)))
      }

      test.unsafeRunSync() should beLeft
    }

    "fail or print succeeds" in {
      val test = {
        val spec = flag("Test", 'x')
        Clipp.parseOrDisplayErrors(List("x"), spec, ()) { _ => IO.unit }
      }

      test.unsafeRunSync()
      ok
    }

    "liftEffect arbitrary effects to the parser" in {
      "success" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftEffect("test", "ex1") {
              IO.pure(verbose.toString)
            }
          } yield result
        ) should beRight()
      }

      "failure" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftEffect("test", "ex1") {
              IO.raiseError(new RuntimeException("lifted function fails"))
            }
          } yield result
        ) should failWithErrors(CustomError("lifted function fails"))
      }
    }
  }

  private def failWithErrors[T](error0: ParserError, errorss: ParserError*): Matcher[Either[ParserFailure, T]] =
    (result: Either[ParserFailure, T]) =>
      result match {
        case Right(_) => ko("Expected failure, got success")
        case Left(ParserFailure(errors, _, _)) =>
          errors should beEqualTo(NonEmptyList(error0, errorss.toList))
      }
}
