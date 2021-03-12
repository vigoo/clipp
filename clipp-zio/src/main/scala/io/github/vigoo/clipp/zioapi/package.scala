package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.free.Free
import zio.{Runtime, ZIO}
import zio.console.{Console, putStrLn}

package object zioapi {
  type ClippEnv = Console
  type ClippZIO[A] = ZIO[ClippEnv, ParserFailure, A]

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

  // TODO: E + remove implicit
  def liftEffect[R, T](description: String, examples: NonEmptyList[T])(f: ZIO[R, String, T])(implicit runtime: Runtime[R]): Free[Parameter, T] =
    syntax.liftEither(description, examples) {
      runtime.unsafeRun(f.either)
    }

  def liftEffect[R, T](description: String, example: T)(f: ZIO[R, String, T])(implicit runtime: Runtime[R]): Free[Parameter, T] =
    syntax.liftEither(description, example) {
      runtime.unsafeRun(f.either)
    }
}
