package io.github.vigoo.clipp

import cats.data.NonEmptyList
import cats.free._
import io.github.vigoo.clipp.choices.Choices
import io.github.vigoo.clipp.errors.ParserError


/**
 * Type class for parsing a command line argument to type T
 *
 * @tparam T
 */
trait ParameterParser[T] {
  /**
   * Parse the command line argument into type T or fail with an error message
   *
   * @param value command line argument
   * @return Either failure or the parsed value
   */
  def parse(value: String): Either[String, T]

  /**
   * An example of the parsed value, used by the usage graph generator to simulate
   * the execution of the parser.
   *
   * It is never used as a result of the parser when it is executed on
   * real input.
   */
  def example: T
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

case class Optional[T](parameter: Parameter.Spec[T])
  extends Parameter[Option[T]] {

  override def toString: String = s"optional $parameter"
}

case class SetMetadata(metadata: ParameterParserMetadata)
  extends Parameter[Unit] {

  override def toString: String = s"set metadata"
}

case class Fail[T](message: String)
  extends Parameter[T] {

  override def toString: String = s"fail with $message"
}

case class Lift[T](f: () => Either[String, T], description: String, examples: NonEmptyList[T])
  extends Parameter[T] {

  override def toString: String = description
}

object Parameter {
  type Spec[T] = Free[Parameter, T]
}

case class ParserFailure(errors: NonEmptyList[ParserError], partialChoices: Choices, spec: Free[Parameter, _])
