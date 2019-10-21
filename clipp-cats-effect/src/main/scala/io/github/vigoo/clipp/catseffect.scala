package io.github.vigoo.clipp

import cats.effect._
import cats.syntax.all._

object catseffect {

  case class ParserFailureException(failure: ParserFailure) extends Exception

  implicit val clippCatsEffect: ClippIO[IO] = new ClippIO[IO] {
    override def succeed[T](value: T): IO[T] =
      IO.pure(value)
    override def failWith[T](parserFailure: ParserFailure): IO[T] =
      IO.raiseError(ParserFailureException(parserFailure))
    override def recoverWith[T](io: IO[T])(f: ParserFailure => IO[T]): IO[T] =
      io.recoverWith {
        case ParserFailureException(failure) => f(failure)
      }

    override def flatMap[T, U](io: IO[T])(f: T => IO[U]): IO[U] =
      io.flatMap(f)

    override def showErrors(errors: String): IO[Unit] =
      IO(println(errors))

    override def showUsage(usageInfo: String): IO[Unit] =
      IO(println(usageInfo))
  }

  object Clipp extends ClippImpl[IO]
}
