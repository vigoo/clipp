package io.github.vigoo.clipp

import java.io.File

import cats.data.NonEmptyList
import cats.free.Free
import org.specs2.mutable._
import syntax._
import parsers._

class ParserSpecs extends Specification {

  private val specFlags = for {
    verbose <- flag("Verbosity", 'v', "verbose")
    extract <- flag("Extract stuff", "extract")
    showStackTrace <- flag("Show stack traces", 'S', "show-stack-trace", "stack-traces")
  } yield (verbose, extract, showStackTrace)

  private val specNamedParams = for {
    name <- namedParameter[String]("User name", "name", 'u', "name", "username")
    password <- namedParameter[String]("Password", "password", "password")
    verbose <- flag("Verbosity", 'v', "verbose")
  } yield (verbose, name, password)

  private val specMix = for {
    name <- namedParameter[String]("User name", "name", 'u', "name", "username")
    verbose <- flag("Verbosity", 'v', "verbose")
    input <- parameter[File]("Input file", "file")
    output <- parameter[File]("Output file", "file")
  } yield (name, verbose, input.getAbsolutePath, output.getAbsolutePath)

  private val specCommand = for {
    verbose <- flag("Verbosity", 'v', "verbose")
    cmd <- command(Set("first", "second"))
    result <- {
      val r: Free[Parameter, Either[(String, String), Boolean]] =
        cmd match {
          case "first" =>
            for {
              name <- namedParameter[String]("User name", "name", 'u', "name", "username")
              password <- namedParameter[String]("Password", "password", "password")
            } yield Left((name, password))
          case "second" =>
            for {
              showStackTrace <- flag("Show stack traces", 'S', "show-stack-trace", "stack-traces")
            } yield Right(showStackTrace)
        }
      r
    }
  } yield (verbose, result)

  "CLI Parameter Parser" should {
    "be able to handle missing flags" in {
      Parser.extractParameters(Array("-v", "--stack-traces"), specFlags) should beRight((true, false, true))
    }

    "be able to parse flags in random order" in {
      Parser.extractParameters(Array("--extract", "-S", "-v"), specFlags) should beRight((true, true, true))
    }

    "report undefined flags" in {
      Parser.extractParameters(Array("--verbose", "-H", "--other"), specFlags) should beLeft(
        NonEmptyList(UnknownParameter("-H"), List(UnknownParameter("--other")))
      )
    }

    "be able to find named parameters" in {
      Parser.extractParameters(Array("--name", "somebody", "--password", "xxx"), specNamedParams) should beRight(
        (false, "somebody", "xxx")
      )
    }

    "be able to find named parameters mixed with flags" in {
      Parser.extractParameters(Array("--password", "xxx", "-v", "-u", "somebody"), specNamedParams) should beRight(
        (true, "somebody", "xxx")
      )
    }

    "report only the missing parameters as missing" in {
      Parser.extractParameters(Array("--password", "xxx", "-v", "-k"), specNamedParams) should beLeft(
        NonEmptyList(MissingNamedParameter(Set("--username", "--name", "-u")), List.empty)
      )
    }

    "simple parameters can be interleaved with others" in {
      Parser.extractParameters(Array("-v", "/home/user/x.in", "--name", "test", "/home/user/x.out"), specMix) should beRight(
        ("test", true, "/home/user/x.in", "/home/user/x.out")
      )
    }

    "optional named parameter works" in {
      val spec = for {
        required <- namedParameter[String]("A required parameter", "value", "required")
        nonRequired <- optional(namedParameter[String]("An optional parameter", "value", "optional"))
      } yield (required, nonRequired)

      Parser.extractParameters(Array("--required", "x", "--optional", "y"), spec) should beRight(
        ("x", Some("y"))
      )

      Parser.extractParameters(Array("--required", "x"), spec) should beRight(
        ("x", None)
      )
    }

    "optional simple parameter works" in {
      val spec = for {
        required <- parameter[String]("A required parameter", "value")
        nonRequired <- optional(parameter[String]("An optional parameter", "value"))
      } yield (required, nonRequired)

      Parser.extractParameters(Array("x", "y"), spec) should beRight(
        ("x", Some("y"))
      )

      Parser.extractParameters(Array("x"), spec) should beRight(
        ("x", None)
      )
    }

    "optional multiple parameter blocks" in {
      val spec = for {
        required <- namedParameter[String]("A required parameter", "value", "required")
        size <- optional {
          for {
            w <- namedParameter[Int]("Width", "integer", 'w', "width")
            h <- namedParameter[Int]("Height", "integer", 'h', "height")
          } yield (w, h)
        }
      } yield (required, size)

      Parser.extractParameters(Array("--required", "test", "-w", "10", "--height", "20"), spec) should beRight(
        ("test", Some((10, 20)))
      )

      Parser.extractParameters(Array("--required", "test", "-w", "10"), spec) should beLeft(
        NonEmptyList(UnknownParameter("-w"), List(UnknownParameter("10")))
      )

      Parser.extractParameters(Array("--required", "test"), spec) should beRight(
        ("test", None)
      )
    }

    "accept valid command" in {
      Parser.extractParameters(Array("first", "--name", "test", "--password", "xxx"), specCommand) should beRight(
        (false, Left(("test", "xxx")))
      )

      Parser.extractParameters(Array("-v", "first", "--name", "test", "--password", "xxx"), specCommand) should beRight(
        (true, Left(("test", "xxx")))
      )

      Parser.extractParameters(Array("-v", "second"), specCommand) should beRight(
        (true, Right(false))
      )
    }

    "refuse invalid command" in {
      Parser.extractParameters(Array("third", "--name", "test", "--password", "xxx"), specCommand) should beLeft(
        NonEmptyList(InvalidCommand("third", Set("first", "second")), List.empty)
      )
    }

    "does not accept parameters after command" in {
      Parser.extractParameters(Array("first", "-v", "--name", "test", "--password", "xxx"), specCommand) should beLeft(
        NonEmptyList(UnknownParameter("-v"), List.empty)
      )
    }

    "dynamic command location is forbidden" in {
      val spec = for {
        b <- flag("Switches a parameter", 'b')
        p <- if (b) parameter[String]("parameter", "value") else Free.pure[Parameter, String]("not specified")
        cmd <- command(Set("first"))
      } yield cmd

      Parser.extractParameters(Array("param", "first", "-b"), spec) should beLeft(
        NonEmptyList(CommandPositionIsNotStatic(Set("first")), List.empty)
      )
    }
  }
}
