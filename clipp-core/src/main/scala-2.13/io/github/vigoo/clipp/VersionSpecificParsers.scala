package io.github.vigoo.clipp

import scala.collection.Factory

trait VersionSpecificParsers {
  implicit def iterableParser[A : ParameterParser, T <: Iterable[A]](separator: Char = ',')(implicit factory: Factory[A, T]): ParameterParser[T] =
    new ParameterParser[T] {
      override def parse(value: String): Either[String, T] = {
        val parts = value.split(separator)
        val builder = factory.newBuilder
        parts
          .map(implicitly[ParameterParser[A]].parse)
          .foldLeft[Either[String, Unit]](Right(())) {
            case (Right(_), Right(item)) =>
              builder.addOne(item)
              Right(())
            case (Right(_), Left(failure)) =>
              Left(failure)
            case (Left(failure), _) =>
              Left(failure)
          }.map(_ => builder.result())
      }

      override def example: T = {
        val builder = factory.newBuilder
        builder.addOne(implicitly[ParameterParser[A]].example)
        builder.result()
      }
    }
}