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
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

object UsageInfoSpecs extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("UsageInfo")(
      test("be a sequence of prints for linear cases") {
        val spec = for {
          name <- namedParameter[String]("User name", "name", 'u', "name", "username")
          verbose <- flag("Verbosity", 'v', "verbose")
          input <- parameter[File]("Input file", "file")
          output <- parameter[File]("Output file", "file")
        } yield (name, verbose, input.getAbsolutePath, output.getAbsolutePath)

        val graph = UsageInfoExtractor.getUsageDescription(spec)
        val usageInfo = UsageInfo.generateUsageInfo(graph)

        assert(usageInfo)(beSequenceOf(
          bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
          bePrintNodeOf(Flag(Some('v'), Set("verbose"), "Verbosity", None)),
          bePrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
          bePrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
        ))
      },

      test("be a sequence of prints for linear case with optional blocks") {
        val spec = for {
          name <- optional {
            namedParameter[String]("User name", "name", 'u', "name", "username")
          }
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

        assert(usageInfo)(beSequenceOf(
          beOptionalPrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
          bePrintNodeOf(Flag(Some('v'), Set("verbose"), "Verbosity", None)),
          beOptionalPrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
          beOptionalPrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
        ))
      },


      test("correctly print a single, flag-dependent option") {
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

        assert(usageInfo)(beSequenceOf(
          bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
          bePrintNodeOf(Flag(Some('i'), Set("have-input"), "Switch for input", None)),
          bePrintChoice(Map(Flag(Some('i'), Set("have-input"), "Switch for input", None) -> Set(BooleanChoice(true)))),
          beStartBranch,
          bePrintNodeOf(SimpleParameter("file", "Input file", None, implicitly[ParameterParser[File]])),
          beExitBranch,
          bePrintNodeOf(SimpleParameter("file", "Output file", None, implicitly[ParameterParser[File]]))
        ))
      },

      test("correctly handle common prefix in branches") {
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

        assert(usageInfo)(beSequenceOf(
          bePrintNodeOf(Flag(None, Set("is3d"), "Is 3D?", None)),
          bePrintNodeOf(NamedParameter(Some('x'), Set.empty, "value", "X", None, implicitly[ParameterParser[Double]])),
          bePrintNodeOf(NamedParameter(Some('y'), Set.empty, "value", "Y", None, implicitly[ParameterParser[Double]])),
          bePrintChoice(Map(Flag(None, Set("is3d"), "Is 3D?", None) -> Set(BooleanChoice(true)))),
          beStartBranch,
          bePrintNodeOf(NamedParameter(Some('z'), Set.empty, "value", "Z", None, implicitly[ParameterParser[Double]])),
          beExitBranch
        ))
      },

      test("correctly print simple command branching") {
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

        assert(usageInfo)(beSequenceOf(
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
        ))
      },

      test("correctly print commands with intersecting subsets") {
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

        println(UsagePrettyPrinter.prettyPrint(graph))

        assert(usageInfo)(beSequenceOf(
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
        ))
      },

      test("custom failures does not break the usage info generator") {
        val spec = for {
          _ <- metadata("test")
          name <- namedParameter[String]("User name", "name", 'u', "name", "username")
          _ <- if (name.length > 16) fail("User name too long") else pure(())
          flag <- flag("Verbose", 'v')
        } yield (name, flag)

        val graph = UsageInfoExtractor.getUsageDescription(spec)
        val usageInfo = UsageInfo.generateUsageInfo(graph)

        assert(usageInfo)(beSequenceOf(
          bePrintNodeOf(NamedParameter(Some('u'), Set("name", "username"), "name", "User name", None, implicitly[ParameterParser[String]])),
          bePrintNodeOf(Flag(Some('v'), Set.empty, "Verbose", None)),
        ))
      },

      test("lifted functions are not executed but the provided single example is used") {
        val builder = new StringBuilder
        val spec = for {
          value <- lift("test", 1) {
            builder.append("hello");
            0
          }
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

        assert(usageInfo)(beSequenceOf(
          bePrintNodeOfLift(),
          bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
        )) && assert(builder.toString())(equalTo(""))
      },

      test("lifted functions are not executed but the provided examples are used") {
        val builder = new StringBuilder
        val spec = for {
          value <- lift("test", NonEmptyList.of(1, 2)) {
            builder.append("hello");
            0
          }
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

        ((assert(usageInfo)(beSequenceOf(
          bePrintNodeOfLift(),
          bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(1)))),
          beStartBranch,
          bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
          beExitBranch,
          bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(2)))),
          beStartBranch,
          bePrintNodeOf(Flag(Some('c'), Set.empty, "c", None)),
          beExitBranch,
        ))) ||
          (assert(usageInfo)(beSequenceOf(
            bePrintNodeOfLift(),
            bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(2)))),
            beStartBranch,
            bePrintNodeOf(Flag(Some('c'), Set.empty, "c", None)),
            beExitBranch,
            bePrintChoiceS(Map("test" -> Set(ArbitraryChoice(1)))),
            beStartBranch,
            bePrintNodeOf(Flag(Some('b'), Set.empty, "b", None)),
            beExitBranch,
          ))) && assert(builder.toString())(equalTo("")))
      }
    )

  private def beSequenceOf(nodes: Assertion[PrettyPrintCommand]*): Assertion[Either[String, Vector[PrettyPrintCommand]]] =
    isRight(
      nodes.zipWithIndex.foldLeft[Assertion[Vector[PrettyPrintCommand]]](hasSize(equalTo(nodes.length))) { case (combined, (assertion, idx)) =>
        combined && hasAt(idx)(assertion)
      }
    )

  private def bePrintNodeOf[T](parameter: Parameter[T]): Assertion[PrettyPrintCommand] = isSubtype[PrintNode](
    hasField("parameter", (node: PrintNode) => node.param.parameter.asInstanceOf[Parameter[T]], equalTo(parameter)) &&
      hasField("isInOptionalBlock", (node: PrintNode) => node.param.isInOptionalBlock, isFalse)
  )

  private def bePrintNodeOfLift[T](): Assertion[PrettyPrintCommand] =
    isSubtype[PrintNode](
      hasField("parameter", (node: PrintNode) => node.param.parameter, isSubtype[Lift[_]](anything)) &&
        hasField("isInOptionalBlock", (node: PrintNode) => node.param.isInOptionalBlock, isFalse)
    )

  private def beOptionalPrintNodeOf[T](parameter: Parameter[T]): Assertion[PrettyPrintCommand] =
    isSubtype[PrintNode](
      hasField("parameter", (node: PrintNode) => node.param.parameter.asInstanceOf[Parameter[T]], equalTo(parameter)) &&
        hasField("isInOptionalBlock", (node: PrintNode) => node.param.isInOptionalBlock, isTrue)
    )

  private def bePrintChoice(expectedChoices: MergedChoices): Assertion[PrettyPrintCommand] =
    isSubtype[PrintChoice](hasField("mergedChoices", _.choice, equalTo(expectedChoices)))

  private def bePrintChoiceS(expectedChoices: Map[String, Set[Choice]]): Assertion[PrettyPrintCommand] =
    isSubtype[PrintChoice](hasField("mergedChoices", _.choice.map { case (k, v) => k.toString -> v }, equalTo(expectedChoices)))

  private def beStartBranch: Assertion[PrettyPrintCommand] =
    isSubtype[StartBranch.type](anything)

  private def beExitBranch: Assertion[PrettyPrintCommand] =
    isSubtype[ExitBranch.type](anything)
}