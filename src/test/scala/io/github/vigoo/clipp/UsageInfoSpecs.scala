package io.github.vigoo.clipp

import java.io.File

import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.usageinfo.debug._
import io.github.vigoo.clipp.usageinfo.UsageInfo._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor.MergedChoices
import io.github.vigoo.clipp.usageinfo.{UsageInfo, UsageInfoExtractor, UsagePrettyPrinter}
import org.specs2.matcher.{MatchResult, Matcher}
import org.specs2.mutable.Specification

class UsageInfoSpecs extends Specification {

  "UsageInfo" should {
    "be a sequence of prints for linear cases" in {
      val spec = for {
        name <- namedParameter[String]("User name", "name", 'u', "name", "username")
        verbose <- flag("Verbosity", 'v', "verbose")
        input <- parameter[File]("Input file", "file")
        output <- parameter[File]("Output file", "file")
      } yield (name, verbose, input.getAbsolutePath, output.getAbsolutePath)

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('v'), Set("verbose"), "Verbosity")),
        bePrintNodeOf(SimpleParameter("file", "Input file", implicitly[ParameterParser[File]])),
        bePrintNodeOf(SimpleParameter("file", "Output file", implicitly[ParameterParser[File]]))
      )
    }

    "correctly print a single, flag-dependent option" in {
      val spec = for {
        name <- namedParameter[String]("User name", "name", 'u', "name", "username")
        haveInput <- flag("Switch for input", 'i', "have-input")
        input <- if (haveInput) {
          parameter[File]("Input file", "file")
        } else {
          pure(new File(""))
        }
        output <- parameter[File]("Output file", "file")
      } yield (name, haveInput, input.getAbsolutePath, output.getAbsolutePath)

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      println(graph.dot)

      val usageInfo = UsageInfo.generateUsageInfo(graph)
      println(usageInfo.right.get.mkString("\n"))
      println(UsagePrettyPrinter.prettyPrint(graph))

      usageInfo should beSequenceOf(
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('i'), Set("have-input"), "Switch for input")),
        bePrintChoice(Map(Flag(Some('i'), Set("have-input"), "Switch for input") -> Set(true))),
        beStartBranch,
        bePrintNodeOf(SimpleParameter("file", "Input file", implicitly[ParameterParser[File]])),
        beExitBranch,
        bePrintNodeOf(SimpleParameter("file", "Output file", implicitly[ParameterParser[File]]))
      )
    }
  }

  private def beSequenceOf(nodes: Matcher[PrettyPrintCommand]*): Matcher[Either[String, Vector[PrettyPrintCommand]]] = (result: Either[String, Vector[PrettyPrintCommand]]) => {
    val nodesWithIndices = nodes.zipWithIndex.toVector

    result match {
      case Right(cmds) =>
        if (cmds.length == nodesWithIndices.length) {
          nodesWithIndices.foldLeft(ok) { case (r, (n, i)) =>
            r and (cmds(i) should n)
          }
        } else {
          ko(s"Expected ${nodesWithIndices.length} elements but there were ${cmds.length} in $cmds")
        }

      case Left(error) =>
        ko(s"Usage info failed with $error")
    }
  }

  private def bePrintNodeOf[T](parameter: Parameter[T]): Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case PrintNode(node) =>
        node.value.parameter should beEqualTo(parameter)
      case command =>
        ko(s"Command needs to be PrintNode but it is $command")
    }
  }

  private def bePrintChoice(expectedChoices: MergedChoices): Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case PrintChoice(mergedChoices) =>
        mergedChoices should beEqualTo(expectedChoices)
      case command =>
        ko(s"Command needs to be PrintChoice but it is $command")
    }
  }

  private def beStartBranch: Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case StartBranch =>
        ok
      case command =>
        ko(s"Command needs to be StartBranch but it is $command")
    }
  }

  private def beExitBranch: Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case ExitBranch =>
        ok
      case command =>
        ko(s"Command needs to be StartBranch but it is $command")
    }
  }
}