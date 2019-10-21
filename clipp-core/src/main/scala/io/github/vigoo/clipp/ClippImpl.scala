package io.github.vigoo.clipp

import cats.free.Free
import io.github.vigoo.clipp.usageinfo.{UsageInfoExtractor, UsagePrettyPrinter}

import scala.language.higherKinds

trait ClippImpl[F[_]] {

  def parseOrFail[T](args: Seq[String], spec: Free[Parameter, T])
                    (implicit cio: ClippIO[F]): F[T] = {
    Parser.extractParameters(args, spec) match {
      case Left(parserFailure) =>
        ClippIO[F].failWith(parserFailure)
      case Right(result) =>
        ClippIO[F].succeed(result)
    }
  }

  def displayErrors(failure: ParserFailure)
                   (implicit cio: ClippIO[F]): F[Unit] = {
    ClippIO[F].showErrors(errors.display(failure.errors))
  }

  def displayErrorsAndUsageInfo[T](spec: Free[Parameter, T])(failure: ParserFailure)
                               (implicit cio: ClippIO[F]): F[Unit] = {
    ClippIO[F].flatMap(ClippIO[F].showErrors(errors.display(failure.errors))) { _ =>
      val usageGraph = UsageInfoExtractor.getUsageDescription(spec, partialChoices = failure.partialChoices)
      val usage = UsagePrettyPrinter.prettyPrint(usageGraph)
      ClippIO[F].showUsage(usage)
    }
  }

  def parseOrDisplayErrors[T](args: Seq[String], spec: Free[Parameter, T])
                             (f: T => F[Unit])
                             (implicit cio: ClippIO[F]): F[Unit] = {
    ClippIO[F].recoverWith(
      ClippIO[F].flatMap(parseOrFail(args, spec))(f))(displayErrors)
  }

  def parseOrDisplayUsageInfo[T](args: Seq[String], spec: Free[Parameter, T])
                                (f: T => F[Unit])
                                (implicit cio: ClippIO[F]): F[Unit] = {
    ClippIO[F].recoverWith(
      ClippIO[F].flatMap(parseOrFail(args, spec))(f))(displayErrorsAndUsageInfo(spec))
  }
}
