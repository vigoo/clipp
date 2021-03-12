package io.github.vigoo.clipp

import cats.data.NonEmptyList
import io.github.vigoo.clipp.errors.{CustomError, ParserError}
import zio._
import zio.test._
import zio.test.Assertion._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.zioapi._
import io.github.vigoo.clipp.zioapi.config._
import zio.console.Console

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
    },

    testM("can provide as layer") {
      val spec = flag("Test", 'x')
      val config: ZLayer[Console, ParserFailure, ClippConfig[Boolean]] = fromArgsWithUsageInfo(List("-x"), spec)
      val test: ZIO[ClippConfig[Boolean], Nothing, TestResult] =
        parameters[Boolean].map(p => assert(p)(isTrue))

      test.provideSomeLayer(config)
    },

    suite("liftEffect")(
      testM("success") {
        ZIO.runtime.flatMap { implicit runtime: Runtime[Any] => // TODO: do not need this

          val spec = liftURIO("test", "ex") {
            ZIO.succeed("test")
          }
          val config: ZLayer[Console, ParserFailure, ClippConfig[String]] = fromArgsWithUsageInfo(List.empty, spec)
          val test: ZIO[ClippConfig[String], Nothing, TestResult] =
            parameters[String].map(p => assert(p)(equalTo("test")))

          test.provideSomeLayer(config)
        }
      },
      testM("failure") {
        ZIO.runtime.flatMap { implicit runtime: Runtime[Any] => // TODO: do not need this

          val spec = liftZIO("test", "ex") {
            ZIO.fail("failure")
          }
          val config: ZLayer[Console, ParserFailure, ClippConfig[String]] = fromArgsWithUsageInfo(List.empty, spec)

          assertM(parameters[String].unit.provideSomeLayer(config).run)(fails(
            hasField("errors", _.errors.toList, contains[ParserError](CustomError("failure")))))
        }
      }
    )
  )
}
