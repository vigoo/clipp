package io.github.vigoo.clipp

import cats.data.NonEmptyList

import java.io.File
import cats.free.Free
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.choices._
import io.github.vigoo.clipp.usageinfo.UsageInfo._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor.MergedChoices
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
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('v'), Set("verbose"), "Verbosity", None)),
        bePrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
        bePrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
      )
    }

    "be a sequence of prints for linear case with optional blocks" in {
      val spec = for {
        name <- optional { namedParameter[String]("User name", "name", 'u', "name", "username") }
        verbose <- flag("Verbosity", 'v', "verbose")
        maybeIo <- optional {
          for {
            i <- parameter[File]("Input file", "file")
            o <- parameter[File]("Output file", "file")
          } yield (i, o)
        }
      } yield (name, verbose, maybeIo)

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        beOptionalPrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('v'), Set("verbose"), "Verbosity", None)),
        beOptionalPrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
        beOptionalPrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
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
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('i'), Set("have-input"), "Switch for input", None)),
        bePrintChoice(Map(Flag(Some('i'), Set("have-input"), "Switch for input", None) -> Set(BooleanChoice(true)))),
        beStartBranch,
        bePrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
        beExitBranch,
        bePrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
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
        bePrintNodeOf(Flag(None, Set("is3d"), "Is 3D?", None)),
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", None, implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(Some('y'), Set.empty, "value", "Y", None, implicitly[ParameterParser[Double]])),
        bePrintChoice(Map(Flag(None, Set("is3d"), "Is 3D?", None) -> Set(BooleanChoice(true)))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", None, implicitly[ParameterParser[Double]])),
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
        bePrintNodeOf(Command(List("a", "b", "c"), None)),
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(CommandChoice("a", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", None, implicitly[ParameterParser[Double]])),
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(CommandChoice("b", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(None, Set("y1"), "value", "Y1", None, implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(None, Set("y2"), "value", "Y2", None, implicitly[ParameterParser[Double]])),
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(CommandChoice("c", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", None, implicitly[ParameterParser[Double]])),
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
        bePrintNodeOf(Flag(Some('v'), Set.empty, "Verbose", None)),
        bePrintNodeOf(Command(List("a", "b", "c"), None)),
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(
          CommandChoice("a", List("a", "b", "c")),
          CommandChoice("c", List("a", "b", "c"))
        ))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", None, implicitly[ParameterParser[Double]])),
        bePrintNodeOf(NamedParameter(Some('y'), Set.empty, "value", "Y", None, implicitly[ParameterParser[Double]])),
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(CommandChoice("c", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", None, implicitly[ParameterParser[Double]])),
        beExitBranch,
        beExitBranch,
        bePrintChoice(Map(Command(List("a", "b", "c"), None) -> Set(CommandChoice("b", List("a", "b", "c"))))),
        beStartBranch,
        bePrintNodeOf(SimpleParameter("name", "Name", None, implicitly[ParameterParser[String]])),
        beExitBranch,
      )
    }

    "custom failures does not break the usage info generator" in {
      val spec = for {
        _ <- metadata("test")
        name <- namedParameter[String]("User name", "name", 'u', "name", "username")
        _ <- if (name.length > 16) fail("User name too long") else pure(())
        flag <- flag("Verbose", 'v')
      } yield (name, flag)

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      usageInfo should beSequenceOf(
        bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
        bePrintNodeOf(Flag(Some('v'), Set.empty, "Verbose", None)),
      )
    }

    "lifted functions are not executed but the provided single example is used" in {
      val builder = new StringBuilder
      val spec = for{
        value <- lift("test", 1) { builder.append("hello"); 0 }
        _ <- value match {
          case 0 =>
            flag("a", 'a')
          case 1 =>
            flag("b", 'b')
          case _ =>
            flag("c", 'c')
        }
      } yield ()

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      (usageInfo should beSequenceOf(
        bePrintNodeOfLift(),
        bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
      )) and (builder.toString() must beEqualTo(""))
    }

    "lifted functions are not executed but the provided examples are used" in {
      val builder = new StringBuilder
      val spec = for{
        value <- lift("test", NonEmptyList.of(1, 2)) { builder.append("hello"); 0 }
        _ <- value match {
          case 0 =>
            flag("a", 'a')
          case 1 =>
            flag("b", 'b')
          case _ =>
            flag("c", 'c')
        }
      } yield ()

      val graph = UsageInfoExtractor.getUsageDescription(spec)
      val usageInfo = UsageInfo.generateUsageInfo(graph)

      println(UsagePrettyPrinter.prettyPrint(graph))

      ((usageInfo should beSequenceOf(
        bePrintNodeOfLift(),
        bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(1)))),
        beStartBranch,
        bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
        beExitBranch,
        bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(2)))),
        beStartBranch,
        bePrintNodeOf(Flag(Some('c'), Set.empty, "c", None)),
        beExitBranch,
      )) or
        (usageInfo should beSequenceOf(
          bePrintNodeOfLift(),
          bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(2)))),
          beStartBranch,
          bePrintNodeOf(Flag(Some('c'), Set.empty, "c", None)),
          beExitBranch,
          bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(1)))),
          beStartBranch,
          bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
          beExitBranch,
        ))) and (builder.toString() must beEqualTo(""))
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
        param.parameter should beEqualTo(parameter) and (param.isInOptionalBlock should beFalse)
      case command =>
        ko(s"Command needs to be PrintNode but it is $command")
    }
  }

  private def bePrintNodeOfLift[T](): Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case PrintNode(param) =>
        param.parameter should beAnInstanceOf[Lift[_]] and (param.isInOptionalBlock should beFalse)
      case command =>
        ko(s"Command needs to be PrintNode but it is $command")
    }
  }

  private def beOptionalPrintNodeOf[T](parameter: Parameter[T]): Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case PrintNode(param) =>
        param.parameter should beEqualTo(parameter) and (param.isInOptionalBlock should beTrue)
      case command =>
        ko(s"Command needs to be an optional PrintNode but it is $command")
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

  private def bePrintChoiceS(expectedChoices: Map[String, Set[Choice]]): Matcher[PrettyPrintCommand] = (cmd: PrettyPrintCommand) => {
    cmd match {
      case PrintChoice(mergedChoices) =>
        mergedChoices.map { case (k, v) => k.toString -> v } should beEqualTo(expectedChoices)
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