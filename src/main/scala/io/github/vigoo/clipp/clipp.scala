package io.github.vigoo.clipp

import java.io.File

import cats.free.Free.liftF
import cats.free._

import scala.util.Try


trait ParameterParser[T] {
  def parse(value: String): Either[String, T]
  def default: T
}


sealed trait Parameter[T]

case class Flag(shortName: Option[Char],
                longNames: Set[String],
                description: String)
  extends Parameter[Boolean] {

  override def toString: String = s"flag $shortName $longNames"
}

case class NamedParameter[T](shortName: Option[Char],
                             longNames: Set[String],
                             placeholder: String,
                             description: String,
                             parameterParser: ParameterParser[T])
  extends Parameter[T] {

  override def toString: String = s"parameter $shortName $longNames"
}

case class SimpleParameter[T](placeholder: String,
                              description: String,
                              parameterParser: ParameterParser[T])
  extends Parameter[T] {

  override def toString: String = s"simple parameter $placeholder ($description)"
}

case class Command(validCommands: Set[String])
  extends Parameter[String] {

  override def toString: String = s"command ($validCommands)"
}

case class Optional[T](parameter: Free[Parameter, T])
  extends Parameter[Option[T]] {

  override def toString: String = s"optional $parameter"
}


sealed trait ParserError
case class UnknownParameter(parameter: String) extends ParserError
case class MissingNamedParameter(variants: Set[String]) extends ParserError
case class MissingValueForNamedParameter(parameter: String) extends ParserError
case class MissingSimpleParameter(placeholder: String) extends ParserError
case class MissingCommand(validCommands: Set[String]) extends ParserError
case class InvalidCommand(command: String, validCommands: Set[String]) extends ParserError
case class FailedToParseValue(message: String, value: String) extends ParserError
case class CommandPositionIsNotStatic(validCommands: Set[String]) extends ParserError


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
    liftF(Flag(Some(shortName), longNames.toSet, description))

  def flag(description: String,
           longName: String,
           otherLongNames: String*): Free[Parameter, Boolean] =
    liftF(Flag(None, otherLongNames.toSet + longName, description))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         shortName: Char,
                                         longNames: String*): Free[Parameter, T] =
    liftF(NamedParameter(Some(shortName), longNames.toSet, placeholder, description, implicitly))

  def namedParameter[T: ParameterParser](description: String,
                                         placeholder: String,
                                         longName: String,
                                         otherLongNames: String*): Free[Parameter, T] =
    liftF(NamedParameter(None, otherLongNames.toSet + longName, placeholder, description, implicitly))

  def parameter[T: ParameterParser](description: String,
                                    placeholder: String): Free[Parameter, T] =
    liftF(SimpleParameter(placeholder, description, implicitly))

  def command(validValues: Set[String]): Free[Parameter, String] =
    liftF(Command(validValues))

  def optional[T](parameter: Free[Parameter, T]): Free[Parameter, Option[T]] =
    liftF[Parameter, Option[T]](Optional(parameter))

  def pure[T](value: T): Free[Parameter, T] =
    Free.pure(value)
}
