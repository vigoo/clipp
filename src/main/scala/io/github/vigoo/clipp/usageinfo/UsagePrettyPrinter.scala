package io.github.vigoo.clipp.usageinfo

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.usageinfo.UsageInfo._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor.ResultGraph

import scala.annotation.tailrec
import scala.collection.mutable

object UsagePrettyPrinter {

  def prettyPrint(usageDescription: ResultGraph): String = {
    UsageInfo.generateUsageInfo(usageDescription) match {
      case Left(failure) =>
        failure
      case Right(commands) =>
        prettyPrint(commands.toList, 0, mutable.StringBuilder.newBuilder)
    }
  }

  private val descriptionX = 40

  private def prettyPrintOptionsAndDesc(level: Int, options: String, description: String, isOptional: Boolean, builder: StringBuilder): Unit = {
    builder.append("  " * level)
    val finalOptions =
      if (isOptional)
        s"[$options]"
      else
        options
    builder.append(finalOptions)
    val remainingSpace = descriptionX - (finalOptions.length + (level * 2))

    if (remainingSpace < 0) {
      builder.append('\n')
      builder.append(" " * descriptionX)
    } else {
      builder.append(" " * remainingSpace)
    }
    builder.append(description)
    if (isOptional) {
      builder.append(" (optional)")
    }
    builder.append('\n')
  }

  private def prettyPrintChoice(choice: (Parameter[_], Set[UsageInfoExtractor.Choice])): String = {

    choice match {
      case (param, choices) =>

        val what =
          param match {
            case Flag(shortName, longNames, _) =>
              shortName.map("-" + _).getOrElse("--" + longNames.head)
            case NamedParameter(shortName, longNames, _, _, _) =>
              shortName.map("-" + _).getOrElse("--" + longNames.head)
            case SimpleParameter(placeholder, _, _) =>
              s"<$placeholder>"
            case Command(_) =>
              "command"
            case Optional(parameter) =>
              throw new IllegalStateException(s"Optionals should have been prefiltered")
          }

        val values = choices.mkString(", ")

        if (choices.size > 1) {
          s"$what is one of $values"
        } else {
          s"$what is $values"
        }
    }
  }

  @tailrec
  private def prettyPrint(cmds: List[PrettyPrintCommand], level: Int, builder: StringBuilder): String =
    cmds match {
      case Nil => builder.toString
      case PrintNode(node) :: remaining =>
        node.value.parameter match {
          case Flag(shortName, longNames, description) =>
            val allOptions = shortName.map("-" + _).toList ::: longNames.map("--" + _).toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, node.value.isInOptionalBlock, builder)

          case NamedParameter(shortName, longNames, placeholder, description, _) =>
            val allOptions = shortName.map(c => s"-$c <$placeholder>").toList ::: longNames.map(n => s"--$n <$placeholder>").toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, node.value.isInOptionalBlock, builder)

          case SimpleParameter(placeholder, description, _) =>
            prettyPrintOptionsAndDesc(level, s"<$placeholder>", description, node.value.isInOptionalBlock, builder)

          case Command(validCommands) =>
            prettyPrintOptionsAndDesc(level, s"<command>", s"One of ${validCommands.mkString(", ")}", node.value.isInOptionalBlock, builder)

          case Optional(parameter) =>
            throw new IllegalStateException(s"Optionals should have been prefiltered")
        }
        prettyPrint(remaining, level, builder)
      case PrintChoice(choice) :: remaining =>
        builder.append('\n')
        builder.append("  " * level)
        builder.append(s"When ${choice.map(prettyPrintChoice).mkString(", ")}:\n")
        prettyPrint(remaining, level, builder)
      case StartBranch :: remaining =>
        prettyPrint(remaining, level + 1, builder)
      case ExitBranch :: remaining =>
        prettyPrint(remaining, level - 1, builder)
    }

}
