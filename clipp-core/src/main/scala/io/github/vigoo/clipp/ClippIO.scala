package io.github.vigoo.clipp

trait ClippIO[F[_]] {
  def succeed[T](value: T): F[T]
  def failWith[T](parserFailure: ParserFailure): F[T]
  def flatMap[T, U](io: F[T])(f: T => F[U]): F[U]
  def recoverWith[T](io: F[T])(f: ParserFailure => F[T]): F[T]
  def showErrors(errors: String): F[Unit]
  def showUsage(usageInfo: String): F[Unit]
}

object ClippIO {
  def apply[F[_] : ClippIO]: ClippIO[F] = implicitly[ClippIO[F]]
}
