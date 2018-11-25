package io.github.vigoo.clipp

import java.io.File

import cats.free.Free.liftF
import cats.free._

import scala.util.Try


trait ParameterParser[T] {
  def parse(value: String): Either[String, T]
  def default: T
}

case class ParameterParserMetadata(programName: String, description: Option[String])


sealed trait Parameter[T]

case class Flag(shortName: Option[Char],
                longNames: Set[String],
                description: String,
                explicitChoices: Option[List[Boolean]])
  extends Parameter[Boolean] {

  override def toString: String = s"flag $shortName $longNames"
}

case class NamedParameter[T](shortName: Option[Char],
                             longNames: Set[String],
                             placeholder: String,
                             description: String,
                             explicitChoices: Option[List[T]],
                             parameterParser: ParameterParser[T])
  extends Parameter[T] {

  override def toString: String = s"parameter $shortName $longNames"
}

case class SimpleParameter[T](placeholder: String,
                              description: String,
                              explicitChoices: Option[List[T]],
                              parameterParser: ParameterParser[T])
  extends Parameter[T] {

  override def toString: String = s"simple parameter $placeholder ($description)"
}

case class Command(validCommands: List[String],
                   explicitChoices: Option[List[String]])
  extends Parameter[String] {

  override def toString: String = s"command ($validCommands)"
}

case class Optional[T](parameter: Free[Parameter, T])
  extends Parameter[Option[T]] {

  override def toString: String = s"optional $parameter"
}

case class SetMetadata(metadata: ParameterParserMetadata)
  extends Parameter[Unit] {

  override def toString: String = s"set metadata"
}


sealed trait ParserError
case class UnknownParameter(parameter: String) extends ParserError
case class MissingNamedParameter(variants: Set[String]) extends ParserError
case class MissingValueForNamedParameter(parameter: String) extends ParserError
case class MissingSimpleParameter(placeholder: String) extends ParserError
case class MissingCommand(validCommands: List[String]) extends ParserError
case class InvalidCommand(command: String, validCommands: List[String]) extends ParserError
case class FailedToParseValue(message: String, value: String) extends ParserError
case class CommandPositionIsNotStatic(validCommands: List[String]) extends ParserError


object parsers {

  implicit val stringParameterParser: ParameterParser[String] = new ParameterParser[String] {
    override def parse(value: String): Either[String, String] = Right(value)

    override def default: String = ""
  }

  implicit val intParameterParser: ParameterParser[Int] = new ParameterParser[Int] {
    override def parse(value: String): Either[String, Int] = Try(value.toInt).toEither.left.map(_.getMessage)

    override def default: Int = 0
  }

  implicit val doubleParameterParser: ParameterParser[Double] = new ParameterParser[Double] {
    override def parse(value: String): Either[String, Double] = Try(value.toDouble).toEither.left.map(_.getMessage)

    override def default: Double = 0.0
  }

  implicit val fileParameterParser: ParameterParser[File] = new ParameterParser[File] {
    override def parse(value: String): Either[String, File] = Right(new File(value))

    override def default: File = new File("x")
  }
}

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
}
