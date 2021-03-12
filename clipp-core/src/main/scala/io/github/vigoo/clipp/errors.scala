package io.github.vigoo.clipp

import cats.data.NonEmptyList

object errors {

  sealed trait ParserError

  case class UnknownParameter(parameter: String) extends ParserError

  case class MissingNamedParameter(variants: Set[String]) extends ParserError

  case class MissingValueForNamedParameter(parameter: String) extends ParserError

  case class MissingSimpleParameter(placeholder: String) extends ParserError

  case class MissingCommand(validCommands: List[String]) extends ParserError

  case class InvalidCommand(command: String, validCommands: List[String]) extends ParserError

  case class FailedToParseValue(message: String, value: String) extends ParserError

  case class CommandPositionIsNotStatic(validCommands: List[String]) extends ParserError

  case class CustomError(message: String) extends ParserError

  def display(errors: NonEmptyList[ParserError]): String = {
    if (errors.length == 1) {
      display(errors.head)
    } else {
      ("Problems:" :: errors.map(display).map(" * " + _).toList).mkString("\n")
    }
  }

  def display(error: ParserError): String =
    error match {
      case UnknownParameter(parameter) => s"Unknown parameter $parameter"
      case MissingNamedParameter(variants) => s"Missing named parameter ${variants.mkString("/")}"
      case MissingValueForNamedParameter(parameter) => s"Named parameter $parameter is missing a value"
      case MissingSimpleParameter(placeholder) => s"Simple parameter $placeholder is missing"
      case MissingCommand(validCommands) => s"Command is missing. Valid commands: ${validCommands.mkString(", ")}"
      case InvalidCommand(command, validCommands) => s"Invalid command $command. Valid commands: ${validCommands.mkString(", ")}"
      case FailedToParseValue(message, value) => s"Failed to parse value $value: $message"
      case CommandPositionIsNotStatic(validCommands) => s"Command position is not static. Valid commands: ${validCommands.mkString(", ")}"
      case CustomError(message) => message
    }
}