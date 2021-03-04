package io.github.vigoo.clipp.usageinfo

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.usageinfo.UsageInfo._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor.{DescribedParameter, UsageDescription}

import scala.annotation.tailrec
import scala.collection.mutable

object UsagePrettyPrinter {
  def prettyPrint(usageDescription: UsageDescription): String = {
    UsageInfo.generateUsageInfo(usageDescription) match {
      case Left(failure) =>
        failure
      case Right(commands) =>
        prettyPrint(commands.toList, usageDescription.metadata)
    }
  }

  def prettyPrint(commandList: List[PrettyPrintCommand], optionalMetadata: Option[ParameterParserMetadata]): String = {
    val builder = new mutable.StringBuilder()

    optionalMetadata.foreach { metadata =>
      prettyPrintUsage(metadata, commandList, builder)
      prettyPrintDescription(metadata, builder)
    }
    prettyPrint(commandList, 1, builder)
  }

  private val descriptionX = 40 // TODO: make this configurable

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

  private def prettyPrintChoice(choice: (Parameter[_], Set[choices.Choice])): String = {

    choice match {
      case (param, choices) =>

        val what =
          param match {
            case Flag(shortName, longNames, _, _) =>
              shortName.map("-" + _).getOrElse("--" + longNames.head)
            case NamedParameter(shortName, longNames, _, _, _, _) =>
              shortName.map("-" + _).getOrElse("--" + longNames.head)
            case SimpleParameter(placeholder, _, _, _) =>
              s"<$placeholder>"
            case Command(_, _) =>
              "command"
            case Optional(parameter) =>
              throw new IllegalStateException(s"Optionals should have been prefiltered")
            case _: SetMetadata =>
              throw new IllegalStateException(s"SetMetadata should have been prefiltered")
            case _: Fail[_] =>
              throw new IllegalStateException(s"Fail should have been prefiltered")
          }

        val values = choices.toList.sorted.map(_.value).mkString(", ")

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
      case PrintNode(param) :: remaining =>
        param.parameter match {
          case Flag(shortName, longNames, description, _) =>
            val allOptions = shortName.map("-" + _).toList ::: longNames.map("--" + _).toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, param.isInOptionalBlock, builder)

          case NamedParameter(shortName, longNames, placeholder, description, _, _) =>
            val allOptions = shortName.map(c => s"-$c <$placeholder>").toList ::: longNames.map(n => s"--$n <$placeholder>").toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, param.isInOptionalBlock, builder)

          case SimpleParameter(placeholder, description, _, _) =>
            prettyPrintOptionsAndDesc(level, s"<$placeholder>", description, param.isInOptionalBlock, builder)

          case Command(validCommands, _) =>
            prettyPrintOptionsAndDesc(level, s"<command>", s"One of ${validCommands.mkString(", ")}", param.isInOptionalBlock, builder)

          case Optional(parameter) =>
            throw new IllegalStateException(s"Optionals should have been prefiltered")

          case _: SetMetadata =>
            throw new IllegalStateException(s"SetMetadata should have been prefiltered")

          case _: Fail[_] =>
            throw new IllegalStateException(s"Fail should have been prefiltered")
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

  private def prettyPrintUsageIfSingle(params: Seq[DescribedParameter], builder: StringBuilder): Unit = {
    params match {
      case Seq() =>
      case param +: Seq() =>
        param.parameter match {
          case flag: Flag =>
            builder.append(" [")
            builder.append(flag.shortName.map(v => s"-$v").getOrElse(s"--${flag.longNames.head}"))
            builder.append(']')
          case namedParam: NamedParameter[_] =>
            builder.append(' ')
            builder.append(namedParam.shortName.map(v => s"-$v").getOrElse(s"--${namedParam.longNames.head}"))
            builder.append(s" <${namedParam.placeholder}>")
          case _ =>
        }
      case _ =>
        builder.append(" [parameters]")
    }
  }

  private def prettyPrintUsage(metadata: ParameterParserMetadata, cmds: List[PrettyPrintCommand], builder: StringBuilder): Unit = {
    builder.append("Usage: ")
    builder.append(metadata.programName)

    var branchDepth = 0
    val paramSet = mutable.Set.empty[DescribedParameter]

    for (cmd <- cmds) {
      cmd match {
        case PrintNode(param) =>
          if (branchDepth == 0) {
            param.parameter match {
              case _: Flag =>
                paramSet += param
              case _: NamedParameter[_] =>
                paramSet += param
              case SimpleParameter(placeholder, description, parameterParser, _) =>
                prettyPrintUsageIfSingle(paramSet.toSeq, builder)
                paramSet.clear()
                if (param.isInOptionalBlock) {
                  builder.append(s" <$placeholder>")
                } else {
                  builder.append(s" [$placeholder]")
                }
              case _: Command =>
                prettyPrintUsageIfSingle(paramSet.toSeq, builder)
                paramSet.clear()
                if (param.isInOptionalBlock) {
                  builder.append(" <command>")
                } else {
                  builder.append(" [command]")
                }
              case Optional(parameter) =>
                throw new IllegalStateException(s"Optionals should have been prefiltered")
              case _: SetMetadata =>
                throw new IllegalStateException(s"SetMetadata should have been prefiltered")
              case _: Fail[_] =>
                throw new IllegalStateException(s"Fail should have been prefiltered")
            }
          }

        case PrintChoice(choice) =>

        case UsageInfo.StartBranch =>
          if (branchDepth == 0) {
            builder.append(" ...")
          }
          branchDepth = branchDepth + 1
        case UsageInfo.ExitBranch =>
          branchDepth = branchDepth - 1
      }
    }

    builder.append("\n\n")
  }

  private def prettyPrintDescription(metadata: ParameterParserMetadata, builder: StringBuilder): Unit = {
    metadata.description.foreach { desc =>
      builder.append(desc)
      builder.append('\n')
    }
  }
}
