package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.free.Free
import cats.free.Free.liftF
import io.github.vigoo.clipp.errors.CustomParserError

import scala.util.Try

trait syntax {
  def flag(description: String,
           shortName: Char, longNames: String*): Free[Parameter, Boolean] =
    liftF(Flag(Some(shortName), longNames.toSet, description, None))

  def flag(description: String,
           longName: String,
           otherLongNames: String*): Free[Parameter, Boolean] =
    liftF(Flag(None, otherLongNames.toSet + longName, description, None))

  def flag(description: String,
           shortName: Char, longNames: List[String],
           withDocumentedChoices: List[Boolean]): Free[Parameter, Boolean] =
    liftF(Flag(Some(shortName), longNames.toSet, description, Some(withDocumentedChoices)))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         shortName: Char,
                                         longNames: String*): Free[Parameter, T] =
    liftF(NamedParameter(Some(shortName), longNames.toSet, placeholder, description, None, implicitly))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         shortName: Char,
                                         longNames: List[String],
                                         withDocumentedChoices: List[T]): Free[Parameter, T] =
    liftF(NamedParameter(Some(shortName), longNames.toSet, placeholder, description, Some(withDocumentedChoices), implicitly))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         longName: String,
                                         otherLongNames: String*): Free[Parameter, T] =
    liftF(NamedParameter(None, otherLongNames.toSet + longName, placeholder, description, None, implicitly))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         longName: String,
                                         otherLongNames: List[String],
                                         withDocumentedChoices: List[T]): Free[Parameter, T] =
    liftF(NamedParameter(None, otherLongNames.toSet + longName, placeholder, description, Some(withDocumentedChoices), implicitly))

  def parameter[T: ParameterParser](description: String,
                                    placeholder: String): Free[Parameter, T] =
    liftF(SimpleParameter(placeholder, description, None, implicitly))

  def parameter[T: ParameterParser](description: String,
                                    placeholder: String,
                                    withDocumentedChoices: List[T]): Free[Parameter, T] =
    liftF(SimpleParameter(placeholder, description, Some(withDocumentedChoices), implicitly))

  def command(validValues: String*): Free[Parameter, String] =
    liftF(Command(validValues.toList, None))

  def command(validValues: List[String], withDocumentedChoices: List[String]): Free[Parameter, String] =
    liftF(Command(validValues, Some(withDocumentedChoices)))

  def optional[T](parameter: Free[Parameter, T]): Free[Parameter, Option[T]] =
    liftF[Parameter, Option[T]](Optional(parameter))

  def metadata(programName: String): Free[Parameter, Unit] =
    liftF[Parameter, Unit](SetMetadata(ParameterParserMetadata(programName, None)))

  def metadata(programName: String, description: String): Free[Parameter, Unit] =
    liftF[Parameter, Unit](SetMetadata(ParameterParserMetadata(programName, Some(description))))

  def pure[T](value: T): Free[Parameter, T] =
    Free.pure(value)

  def fail[E : CustomParserError, T](failure: E): Free[Parameter, T] =
    liftF[Parameter, T](Fail(CustomParserError.toMessage(failure)))

  def liftEither[E: CustomParserError, T](description: String, examples: NonEmptyList[T])(f: => Either[E, T]): Free[Parameter, T] =
    liftF[Parameter, T](Lift(() => f.left.map(CustomParserError.toMessage[E]), description, examples))

  def liftEither[E: CustomParserError, T](description: String, example: T)(f: => Either[E, T]): Free[Parameter, T] =
    liftF[Parameter, T](Lift(() => f.left.map(CustomParserError.toMessage[E]), description, NonEmptyList.one(example)))

  def liftTry[T](description: String, examples: NonEmptyList[T])(f: => Try[T]): Free[Parameter, T] =
    liftEither(description, examples)(f.toEither)

  def liftTry[T](description: String, example: T)(f: => Try[T]): Free[Parameter, T] =
    liftEither(description, example)(f.toEither)

  def lift[T](description: String, examples: NonEmptyList[T])(f: => T): Free[Parameter, T] =
    liftTry(description, examples)(Try(f))

  def lift[T](description: String, example: T)(f: => T): Free[Parameter, T] =
    liftTry(description, example)(Try(f))
}

object syntax extends syntax