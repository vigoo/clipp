package io.github.vigoo.clipp

import java.io.File

import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.usageinfo.{UsageInfoExtractor, UsagePrettyPrinter}

object Playground {

  sealed trait TestCommand

  case class Command1(name: String) extends TestCommand

  case class Command2(x: Int, y: Int) extends TestCommand

  case class TestParameters(verbose: Boolean,
                            input: File,
                            output: Option[File],
                            weight: Option[Double],
                            command: TestCommand)

  def main(args: Array[String]): Unit = {
    val paramSpec =
      for {
        _ <- metadata(programName = "playground", description = "Testing some features of the new CLI parameter parser")
        verbose <- flag("Verbose output", 'v', "verbose")
        input <- parameter[File]("Input file's path", "file")
        output <- optional { parameter[File]("Output file's path", "file") }
        weight <- if (verbose) {
          optional { namedParameter[Double]("Weight parameter", "value", "weight") }
        } else {
          pure(None)
        }
        commandName <- command("cmd1", "cmd2")
        command <- commandName match {
          case "cmd1" =>
            for {
              name <- parameter[String]("Name", "name")
            } yield Command1(name)
          case "cmd2" =>
            for {
              x <- namedParameter[Int]("X", "value", 'x')
              y <- if (verbose) {
                namedParameter[Int]("Y", "value", 'y')
              } else {
                pure(10)
              }
            } yield Command2(x, y)
        }

      } yield TestParameters(verbose, input, output, weight, command)

    Parser.extractParameters(args, paramSpec) match {
      case Left(error) =>
        println(errors.display(error.errors))

        val usageGraph = UsageInfoExtractor.getUsageDescription(paramSpec, error.partialChoices)
        println(UsagePrettyPrinter.prettyPrint(usageGraph))
      case Right(result) =>
        println(result)
    }
  }
}
