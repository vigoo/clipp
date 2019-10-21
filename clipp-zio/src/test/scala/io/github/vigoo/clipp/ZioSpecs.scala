package io.github.vigoo.clipp

import org.specs2.mutable.Specification
import _root_.zio._

import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._

import zio._

class ZioSpecs extends Specification with DefaultRuntime {

  "ZIO interface" should {
    "successfully parse" in {
      val result = unsafeRun {
        val spec = flag("Test", 'x')
        Clipp.parseOrFail[Boolean](List("-x"), spec)
      }

      result === true
    }

    "fail on bad spec" in {
      val result = unsafeRun {
        val spec = flag("Test", 'x')
        Clipp.parseOrFail[Boolean](List("x"), spec).either
      }

      result should beLeft
    }

    "fail or print succeeds" in {
      unsafeRun {
        val spec = flag("Test", 'x')
        Clipp.parseOrDisplayErrors(List("x"), spec) { _ => ZIO.unit }
      }

      ok
    }
  }
}
