package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.effect._
import cats.effect.unsafe.implicits.global
import io.github.vigoo.clipp.catseffect3._
import io.github.vigoo.clipp.errors.{CustomError, ParserError}
import io.github.vigoo.clipp.syntax._
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

object CatsEffectSpecs extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Cats Effect 3 interface")(
      test("successfully parse") {
        val test = {
          val spec = flag("Test", 'x')
          Clipp.parseOrFail[Boolean](List("-x"), spec)
        }

        assert(test.unsafeRunSync())(isTrue)
      },

      test("fail on bad spec") {
        val test = {
          val spec = flag("Test", 'x')
          Clipp.parseOrFail[Boolean](List("x"), spec)
            .map(Right.apply[Throwable, Boolean])
            .handleErrorWith(error => IO.pure(Left.apply[Throwable, Boolean](error)))
        }

        assert(test.unsafeRunSync())(isLeft(anything))
      },

      test("fail or print succeeds") {
        val test = {
          val spec = flag("Test", 'x')
          Clipp.parseOrDisplayErrors(List("x"), spec, ()) { _ => IO.unit }
        }

        assert(test.unsafeRunSync())(isUnit)
      },

      suite("liftEffect arbitrary effects to the parser")(
        test("success") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftEffect("test", "ex1") {
                IO.pure(verbose.toString)
              }
            } yield result
          ))(isRight(anything))
        },

        test("failure") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftEffect("test", "ex1") {
                IO.raiseError(new RuntimeException("lifted function fails"))
              }
            } yield result
          ))(failWithErrors(CustomError("lifted function fails")))
        }
      )
    )

  private def failWithErrors[T](error0: ParserError, errorss: ParserError*): Assertion[Either[ParserFailure, T]] =
    isLeft(hasField("errors", (f: ParserFailure) => f.errors, equalTo(NonEmptyList(error0, errorss.toList))))
}
