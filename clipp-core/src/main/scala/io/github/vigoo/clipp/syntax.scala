package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.free.Free
import cats.free.Free.liftF

import scala.util.Try

object syntax {

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

  def fail[T](message: String): Free[Parameter, T] =
    liftF[Parameter, T](Fail(message))

  def liftEither[T](description: String, examples: NonEmptyList[T])(f: => Either[String, T]): Free[Parameter, T] =
    liftF[Parameter, T](Lift(() => f, description, examples))

  def liftEither[T](description: String, example: T)(f: => Either[String, T]): Free[Parameter, T] =
    liftF[Parameter, T](Lift(() => f, description, NonEmptyList.one(example)))

  def liftTry[T](description: String, examples: NonEmptyList[T])(f: => Try[T]): Free[Parameter, T] =
    liftEither(description, examples)(f.toEither.left.map(_.getMessage))

  def liftTry[T](description: String, example: T)(f: => Try[T]): Free[Parameter, T] =
    liftEither(description, example)(f.toEither.left.map(_.getMessage))

  def lift[T](description: String, examples: NonEmptyList[T])(f: => T): Free[Parameter, T] =
    liftTry(description, examples)(Try(f))

  def lift[T](description: String, example: T)(f: => T): Free[Parameter, T] =
    liftTry(description, example)(Try(f))
}
