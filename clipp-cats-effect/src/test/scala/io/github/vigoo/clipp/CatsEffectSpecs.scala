package io.github.vigoo.clipp

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.catseffect._
import cats.effect._
import cats.effect.syntax._
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
        Clipp.parseOrDisplayErrors(List("x"), spec) { _ => IO.unit }
      }

      test.unsafeRunSync()
      ok
    }
  }
}
