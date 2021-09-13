package io.github.vigoo.clipp

import io.github.vigoo.clipp.errors.{CustomError, ParserError}
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.zioapi._
import zio.{Console, _}
import zio.test.Assertion._
import zio.test._

object ZioSpecs extends DefaultRunnableSpec {

  def spec = suite("ZIO interface")(
    test("successfully parse") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrFail[Boolean](List("-x"), spec)
      } yield assert(result)(isTrue)
    },

    test("fail on bad spec") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrFail[Boolean](List("x"), spec).either
      } yield assert(result)(isLeft(anything))
    },

    test("fail or print succeeds") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrDisplayErrors(List("x"), spec, ()) { _ => ZIO.unit }
      } yield assert(result)(anything)
    },

    test("can be used with return values") {
      val spec = flag("Test", 'x')
      for {
        result <- Clipp.parseOrDisplayErrors(List("-x"), spec, ExitCode.failure) { _ => ZIO.succeed(ExitCode.success) }
      } yield assert(result)(equalTo(ExitCode.success))
    },

    test("can provide as layer") {
      val spec = flag("Test", 'x')
      val config: ZLayer[Has[Console] with Has[ZIOAppArgs], ParserFailure, Has[Boolean]] = parametersFromArgs(spec).printUsageInfoOnFailure
      val test: ZIO[Has[Boolean], Nothing, TestResult] =
        parameters[Boolean].map(p => assert(p)(isTrue))

      test.injectCustom(ZLayer.succeed(ZIOAppArgs(Chunk("-x"))), config)
    },

    suite("liftEffect")(
      test("success") {
        val config = effectfulParametersFromArgs[Any, String] { p =>
          p.liftURIO("test", "ex") {
            ZIO.succeed("test")
          }
        }
        val test: ZIO[Has[String], Nothing, TestResult] =
          parameters[String].map(p => assert(p)(equalTo("test")))

        test.injectCustom(ZLayer.succeed(ZIOAppArgs(Chunk.empty)), config)
      },
      test("failure") {
        val config = effectfulParametersFromArgs[Any, String] { p =>
          p.liftZIO("test", "ex") {
            ZIO.fail("failure")
          }
        }

        assertM(parameters[String].unit.injectCustom(ZLayer.succeed(ZIOAppArgs(Chunk.empty)), config).exit)(fails(
          hasField("errors", _.errors.toList, contains[ParserError](CustomError("failure")))))
      }
    )
  )
}
