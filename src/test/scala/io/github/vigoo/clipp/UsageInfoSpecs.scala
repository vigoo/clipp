package io.github.vigoo.clipp

import java.io.File

import cats.free.Free
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.usageinfo.UsageInfo._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor.{BooleanChoice, CommandChoice, MergedChoices}
import io.github.vigoo.clipp.usageinfo.debug._
import io.github.vigoo.clipp.usageinfo.{UsageInfo, UsageInfoExtractor, UsagePrettyPrinter}
import org.specs2.matcher.Matcher
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
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('i'), Set("have-input"), "Switch for input")),
        bePrintChoice(Map(Flag(Some('i'), Set("have-input"), "Switch for input") -> Set(BooleanChoice(true)))),
        beStartBranch,
        bePrintNodeOf(SimpleParameter("file", "Input file", implicitly[ParameterParser[File]])),
        beExitBranch,
        bePrintNodeOf(SimpleParameter("file", "Output file", implicitly[ParameterParser[File]]))
      )
    }

    "correctly handle common prefix in branches" in {
      val spec: Free[Parameter, Either[(Double, Double, Double), (Double, Double)]] = for {
        is3d <- flag("Is 3D?", "is3d")
        result <- if (is3d) {
          for {
            x <- namedParameter[Double]("X", "value", 'x')
            y <- namedParameter[Double]("Y", "value", 'y')
            z <- namedParameter[Double]("Z", "value", 'z')
          } yield Left(x, y, z)
        } else {
          for {
            x <- namedParameter[Double]("X", "value", 'x')
            y <- namedParameter[Double]("Y", "value", 'y')
          } yield Right(x, y)
        }
      } yield result

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(Flag(None, Set("is3d"), "Is 3D?")),
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(Some('y'), Set.empty, "value", "Y", implicitly[ParameterParser[Double]])),
        bePrintChoice(Map(Flag(None, Set("is3d"), "Is 3D?") -> Set(BooleanChoice(true)))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", implicitly[ParameterParser[Double]])),
        beExitBranch
      )
    }

    "correctly print simple command branching" in {
      val spec = for {
        cmd <- command("a", "b", "c")
        result <- cmd match {
          case "a" =>
            for {
              x <- namedParameter[Double]("X", "value", 'x')
            } yield 0
          case "b" =>
            for {
              y1 <- namedParameter[Double]("Y1", "value", "y1")
              y2 <- namedParameter[Double]("Y2", "value", "y2")
            } yield 1
          case "c" =>
            for {
              z <- namedParameter[Double]("Z", "value", 'z')
            } yield 2
        }
      } yield result

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(Command(List("a", "b", "c"))),
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(CommandChoice("a", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", implicitly[ParameterParser[Double]])),
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(CommandChoice("b", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(None, Set("y1"), "value", "Y1", implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(None, Set("y2"), "value", "Y2", implicitly[ParameterParser[Double]])),
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(CommandChoice("c", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", implicitly[ParameterParser[Double]])),
        beExitBranch
      )
    }

    "correctly print commands with intersecting subsets" in {
      val spec = for {
        flag <- flag("Verbose", 'v')
        cmd <- command("a", "b", "c")
        result <- cmd match {
          case "a" =>
            for {
              x <- namedParameter[Double]("X", "value", 'x')
              y <- namedParameter[Double]("Y", "value", 'y')
            } yield 0
          case "b" =>
            parameter[String]("Name", "name").map(_ => 1)
          case "c" =>
            for {
              x <- namedParameter[Double]("X", "value", 'x')
              y <- namedParameter[Double]("Y", "value", 'y')
              z <- namedParameter[Double]("Z", "value", 'z')
            } yield 2
        }
      } yield (flag, result)

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(Flag(Some('v'), Set.empty, "Verbose")),
        bePrintNodeOf(Command(List("a", "b", "c"))),
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(
          CommandChoice("a", List("a", "b", "c")),
          CommandChoice("c", List("a", "b", "c"))
        ))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(Some('y'), Set.empty, "value", "Y", implicitly[ParameterParser[Double]])),
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(CommandChoice("c", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", implicitly[ParameterParser[Double]])),
        beExitBranch,
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c")) -> Set(CommandChoice("b", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(SimpleParameter("name", "Name", implicitly[ParameterParser[String]])),
        beExitBranch,
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
      case PrintNode(param) =>
        param.parameter should beEqualTo(parameter)
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