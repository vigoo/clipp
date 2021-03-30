package io.github.vigoo.clipp

import java.io.File
import cats.data.NonEmptyList
import cats.free.Free
import io.github.vigoo.clipp.errors._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable._

import scala.util.{Failure, Success}

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

  private val specMultiNamedParams = for {
    names <- repeated { namedParameter[String]("Name", "name", 'n', "name", "username") }
    _ <- namedParameter[String]("other", "other", "other")
  } yield names

  private val specMix = for {
    name <- namedParameter[String]("User name", "name", 'u', "name", "username")
    verbose <- flag("Verbosity", 'v', "verbose")
    input <- parameter[File]("Input file", "file")
    output <- parameter[File]("Output file", "file")
  } yield (name, verbose, input.toPath, output.toPath)

  private val specCommand = for {
    verbose <- flag("Verbosity", 'v', "verbose")
    cmd <- command("first", "second")
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

  private val specCustomFailure = for {
    _ <- metadata("test")
    name <- optional(namedParameter[String]("User name", "name", 'u', "name", "username"))
    result <- name match {
      case Some(value) => pure(value)
      case None => fail[String, String]("name was not defined")
    }
  } yield result

  "CLI Parameter Parser" should {
    "be able to handle missing flags" in {
      Parser.extractParameters(Seq("-v", "--stack-traces"), specFlags) should beRight((true, false, true))
    }

    "be able to parse flags in random order" in {
      Parser.extractParameters(Seq("--extract", "-S", "-v"), specFlags) should beRight((true, true, true))
    }

    "report undefined flags" in {
      Parser.extractParameters(Seq("--verbose", "-H", "--other"), specFlags) should failWithErrors(
        UnknownParameter("-H"), UnknownParameter("--other")
      )
    }

    "be able to find named parameters" in {
      Parser.extractParameters(Seq("--name", "somebody", "--password", "xxx"), specNamedParams) should beRight(
        (false, "somebody", "xxx")
      )
    }

    "be able to find named parameters mixed with flags" in {
      Parser.extractParameters(Seq("--password", "xxx", "-v", "-u", "somebody"), specNamedParams) should beRight(
        (true, "somebody", "xxx")
      )
    }

    "report only the missing parameters as missing" in {
      Parser.extractParameters(Seq("--password", "xxx", "-v", "-k"), specNamedParams) should failWithErrors(
        MissingNamedParameter(Set("--username", "--name", "-u"))
      )
    }

    "simple parameters can be interleaved with others" in {
      Parser.extractParameters(Seq("-v", "/home/user/x.in", "--name", "test", "/home/user/x.out"), specMix) should beRight(
        ("test", true, new File("/home/user/x.in").toPath, new File("/home/user/x.out").toPath)
      )
    }

    "be able to collect multiple named parameters" in {
      Parser.extractParameters(Seq("--name", "1", "--other", "param", "--username", "2", "-n", "3"), specMultiNamedParams) should beRight(
        (List("1", "2", "3"))
      )
    }

    "optional named parameter works" in {
      val spec = for {
        required <- namedParameter[String]("A required parameter", "value", "required")
        nonRequired <- optional(namedParameter[String]("An optional parameter", "value", "optional"))
      } yield (required, nonRequired)

      Parser.extractParameters(Seq("--required", "x", "--optional", "y"), spec) should beRight(
        ("x", Some("y"))
      )

      Parser.extractParameters(Seq("--required", "x"), spec) should beRight(
        ("x", None)
      )
    }

    "optional simple parameter works" in {
      val spec = for {
        required <- parameter[String]("A required parameter", "value")
        nonRequired <- optional(parameter[String]("An optional parameter", "value"))
      } yield (required, nonRequired)

      Parser.extractParameters(Seq("x", "y"), spec) should beRight(
        ("x", Some("y"))
      )

      Parser.extractParameters(Seq("x"), spec) should beRight(
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

      Parser.extractParameters(Seq("--required", "test", "-w", "10", "--height", "20"), spec) should beRight(
        ("test", Some((10, 20)))
      )

      Parser.extractParameters(Seq("--required", "test", "-w", "10"), spec) should failWithErrors(
        UnknownParameter("-w"), UnknownParameter("10")
      )

      Parser.extractParameters(Seq("--required", "test"), spec) should beRight(
        ("test", None)
      )
    }

    "accept valid command" in {
      Parser.extractParameters(Seq("first", "--name", "test", "--password", "xxx"), specCommand) should beRight(
        (false, Left(("test", "xxx")))
      )

      Parser.extractParameters(Seq("-v", "first", "--name", "test", "--password", "xxx"), specCommand) should beRight(
        (true, Left(("test", "xxx")))
      )

      Parser.extractParameters(Seq("-v", "second"), specCommand) should beRight(
        (true, Right(false))
      )
    }

    "refuse invalid command" in {
      Parser.extractParameters(Seq("third", "--name", "test", "--password", "xxx"), specCommand) should failWithErrors(
        InvalidCommand("third", List("first", "second"))
      )
    }

    "does not accept parameters after command" in {
      Parser.extractParameters(Seq("first", "-v", "--name", "test", "--password", "xxx"), specCommand) should failWithErrors(
        UnknownParameter("-v")
      )
    }

    "custom failure with valid input" in {
      Parser.extractParameters(Seq("--name", "test"), specCustomFailure) should beRight("test")
    }

    "custom failure with invalid input" in {
      Parser.extractParameters(Seq.empty, specCustomFailure) should failWithErrors(
        CustomError("name was not defined")
      )
    }

    "lift arbitrary functions to the parser" in{
      "success" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- lift("test", "ex1") { verbose.toString }
          } yield result
        ) should beRight(beEqualTo("true"))
      }

      "is suspended" in {
        val builder = new StringBuilder
        val spec = for {
          verbose <- flag("verbose", 'v')
          result <- lift("test", "ex1") { builder.append("hello"); verbose.toString }
        } yield result

        val before = builder.toString()

        val result = Parser.extractParameters(
          Seq("-v"),
          spec
        )
        val after = builder.toString()

        (before must beEqualTo("")) and
          (result should beRight(beEqualTo("true"))) and
          (after must not(beEqualTo(""))) // NOTE: not guaranteed that the effect runs only once
      }

      "throwing exception" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- lift("test", "ex1") { throw new RuntimeException("lifted function fails") }
          } yield result
        ) should failWithErrors(CustomError("lifted function fails"))
      }
    }

    "liftEither arbitrary functions to the parser" in {
      "success" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftEither[String, String]("test", "ex1") { Right(verbose.toString) }
          } yield result
        ) should beRight(beEqualTo("true"))
      }

      "failure" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftEither("test", "ex1") { Left("lifted function fails") }
          } yield result
        ) should failWithErrors(CustomError("lifted function fails"))
      }
    }

    "liftTry arbitrary functions to the parser" in {
      "success" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftTry("test", "ex1") { Success(verbose.toString) }
          } yield result
        ) should beRight(beEqualTo("true"))
      }

      "failure" in {
        Parser.extractParameters(
          Seq("-v"),
          for {
            verbose <- flag("verbose", 'v')
            result <- liftTry("test", "ex1") { Failure(new RuntimeException("lifted function fails")) }
          } yield result
        ) should failWithErrors(CustomError("lifted function fails"))
      }
    }
  }

  private def failWithErrors[T](error0: ParserError, errorss: ParserError*): Matcher[Either[ParserFailure, T]] =
    (result: Either[ParserFailure, T]) =>
      result match {
        case Right(_) => ko("Expected failure, got success")
        case Left(ParserFailure(errors, _, _)) =>
          errors should beEqualTo(NonEmptyList(error0, errorss.toList))
      }
}