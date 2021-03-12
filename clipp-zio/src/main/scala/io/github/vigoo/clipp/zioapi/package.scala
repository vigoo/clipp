package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.free.Free
import io.github.vigoo.clipp.errors.CustomParserError
import zio.console.{Console, putStrLn}
import zio.{CanFail, Has, Runtime, Tag, URIO, ZIO, ZLayer}

package object zioapi {
  type ClippEnv = Console
  type ClippZIO[+A] = ZIO[ClippEnv, ParserFailure, A]

  implicit val clippZio: ClippIO[ClippZIO] = new ClippIO[ClippZIO] {
    override def succeed[T](value: T): ClippZIO[T] =
      ZIO.succeed(value)
    override def failWith[T](parserFailure: ParserFailure): ClippZIO[T] =
      ZIO.fail(parserFailure)

    override def recoverWith[T](io: ClippZIO[T])(f: ParserFailure => ClippZIO[T]): ClippZIO[T] =
      io.foldM(f, v => ZIO.succeed(v))

    override def flatMap[T, U](io: ClippZIO[T])(f: T => ClippZIO[U]): ClippZIO[U] =
      io.flatMap(f)

    override def showErrors(errors: String): ClippZIO[Unit] =
      putStrLn(errors)

    override def showUsage(usageInfo: String): ClippZIO[Unit] =
      putStrLn(usageInfo)
  }

  object Clipp extends ClippImpl[ClippZIO]

  def liftZIO[R, E, T](description: String, examples: NonEmptyList[T])(f: ZIO[R, E, T])(implicit runtime: Runtime[R], ev: CanFail[E], customParserError: CustomParserError[E]): Parameter.Spec[T] =
    syntax.liftEither(description, examples) {
      runtime.unsafeRun(f.either)
    }

  def liftZIO[R, E, T](description: String, example: T)(f: ZIO[R, E, T])(implicit runtime: Runtime[R], ev: CanFail[E], customParserError: CustomParserError[E]): Parameter.Spec[T] =
    syntax.liftEither(description, example) {
      runtime.unsafeRun(f.either)
    }

  def liftURIO[R, T](description: String, examples: NonEmptyList[T])(f: URIO[R, T])(implicit runtime: Runtime[R]): Parameter.Spec[T] =
    syntax.lift(description, examples) {
      runtime.unsafeRun(f)
    }

  def liftURIO[R, T](description: String, example: T)(f: URIO[R, T])(implicit runtime: Runtime[R]): Parameter.Spec[T] =
    syntax.lift(description, example) {
      runtime.unsafeRun(f)
    }

  case class ZioDSL[R](runtime: Runtime[R]) extends syntax {
    private implicit val r: Runtime[R] = runtime

    def liftZIO[E, T](description: String, examples: NonEmptyList[T])(f: ZIO[R, E, T])(implicit ev: CanFail[E], customParserError: CustomParserError[E]): Parameter.Spec[T] =
      zioapi.liftZIO[R, E, T](description, examples)(f)

    def liftZIO[E, T](description: String, example: T)(f: ZIO[R, E, T])(implicit ev: CanFail[E], customParserError: CustomParserError[E]): Parameter.Spec[T] =
      zioapi.liftZIO[R, E, T](description, example)(f)

    def liftURIO[T](description: String, examples: NonEmptyList[T])(f: URIO[R, T]): Parameter.Spec[T] =
      zioapi.liftURIO[R, T](description, examples)(f)

    def liftURIO[T](description: String, example: T)(f: URIO[R, T]): Parameter.Spec[T] =
      zioapi.liftURIO[R, T](description, example)(f)
  }

  def parametersFromArgs[T : Tag](args: List[String], spec: Parameter.Spec[T]): ZLayer[Console, ParserFailure, Has[T]] =
    Clipp.parseOrFail(args, spec)
      .toLayer

  def effectfulParametersFromArgs[R, T : Tag](args: List[String])(createSpec: ZioDSL[R] => Parameter.Spec[T]): ZLayer[Console with R, ParserFailure, Has[T]] = {
    for {
      runtime <- ZIO.runtime[R]
      spec = createSpec(ZioDSL(runtime))
      result <- Clipp.parseOrFail(args, spec)
    } yield result
  }.toLayer

  implicit class ZLayerOps[R <: Console, T](layer: ZLayer[R, ParserFailure, T]) {
    def printUsageInfoOnFailure: ZLayer[R, ParserFailure, T] =
      layer.tapError { parserFailure: ParserFailure =>
        Clipp.displayErrorsAndUsageInfo(parserFailure)
      }
  }
  def parametersFromArgsWithUsageInfo[T : Tag](args: List[String], spec: Parameter.Spec[T]): ZLayer[Console, ParserFailure, Has[T]] =
    parametersFromArgs(args, spec)
      .tapError { parserFailure: ParserFailure =>
        Clipp.displayErrorsAndUsageInfo(parserFailure)
      }

  def parameters[T : Tag]: URIO[Has[T], T] = ZIO.service[T]
}
