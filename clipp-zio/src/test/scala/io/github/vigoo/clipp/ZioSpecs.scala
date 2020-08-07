package io.github.vigoo.clipp

import _root_.zio._
import _root_.zio.test._
import _root_.zio.test.Assertion._

import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._

import zioapi._

object ZioSpecs extends DefaultRunnableSpec {

  def spec = suite("ZIO interface")(
    testM("successfully parse") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrFail[Boolean](List("-x"), spec)
      } yield assert(result)(isTrue)
    },

    testM("fail on bad spec") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrFail[Boolean](List("x"), spec).either
      } yield assert(result)(isLeft(anything))
    },

    testM("fail or print succeeds") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrDisplayErrors(List("x"), spec, ()) { _ => ZIO.unit }
      } yield assert(result)(anything)
    },

    testM("can be used with return values") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrDisplayErrors(List("-x"), spec, ExitCode.failure) { _ => ZIO.succeed(ExitCode.success) }
      } yield assert(result)(equalTo(ExitCode.success))
    }
  )
}
