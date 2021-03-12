package io.github.vigoo.clipp

import scala.collection.generic.CanBuildFrom

trait VersionSpecificParsers {
  implicit def iterableParser[A: ParameterParser, T <: Iterable[A]](separator: Char = ',')(implicit bf: CanBuildFrom[Seq[A], A, T]): ParameterParser[T] =
    new ParameterParser[T] {
      override def parse(value: String): Either[String, T] = {
        val parts = value.split(separator)
        val builder = bf()
        parts
          .map(implicitly[ParameterParser[A]].parse)
          .foldLeft[Either[String, Unit]](Right(())) {
            case (Right(_), Right(item)) =>
              builder += item
              Right(())
            case (Right(_), Left(failure)) =>
              Left(failure)
            case (Left(failure), _) =>
              Left(failure)
          }.map(_ => builder.result())
      }

      override def example: T = {
        val builder = bf()
        builder += implicitly[ParameterParser[A]].example
        builder.result()
      }
    }

}