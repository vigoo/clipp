package io.github.vigoo.clipp

import java.io.File
import cats.data.NonEmptyList
import cats.free.Free
import io.github.vigoo.clipp.errors._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

import scala.util.{Failure, Success}

object ParserSpecs extends DefaultRunnableSpec {

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
    names <- repeated {
      namedParameter[String]("Name", "name", 'n', "name", "username")
    }
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

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("CLI Parameter Parser")(
      test("be able to handle missing flags") {
        assert(Parser.extractParameters(Seq("-v", "--stack-traces"), specFlags))(isRight(equalTo((true, false, true))))
      },

      test("be able to parse flags in random order") {
        assert(Parser.extractParameters(Seq("--extract", "-S", "-v"), specFlags))(isRight(equalTo((true, true, true))))
      },

      test("report undefined flags") {
        assert(Parser.extractParameters(Seq("--verbose", "-H", "--other"), specFlags))(failWithErrors(
          UnknownParameter("-H"), UnknownParameter("--other")
        ))
      },

      test("be able to find named parameters") {
        assert(Parser.extractParameters(Seq("--name", "somebody", "--password", "xxx"), specNamedParams))(isRight(equalTo(
          (false, "somebody", "xxx")
        )))
      },

      test("be able to find named parameters mixed with flags") {
        assert(Parser.extractParameters(Seq("--password", "xxx", "-v", "-u", "somebody"), specNamedParams))(isRight(equalTo(
          (true, "somebody", "xxx")
        )))
      },

      test("report only the missing parameters as missing") {
        assert(Parser.extractParameters(Seq("--password", "xxx", "-v", "-k"), specNamedParams))(failWithErrors(
          MissingNamedParameter(Set("--username", "--name", "-u"))
        ))
      },

      test("simple parameters can be interleaved with others") {
        assert(Parser.extractParameters(Seq("-v", "/home/user/x.in", "--name", "test", "/home/user/x.out"), specMix))(isRight(equalTo(
          ("test", true, new File("/home/user/x.in").toPath, new File("/home/user/x.out").toPath)
        )))
      },

      test("be able to collect multiple named parameters") {
        assert(Parser.extractParameters(Seq("--name", "1", "--other", "param", "--username", "2", "-n", "3"), specMultiNamedParams))(isRight(equalTo(
          List("1", "2", "3")
        )))
      },

      test("optional named parameter works") {
        val spec = for {
          required <- namedParameter[String]("A required parameter", "value", "required")
          nonRequired <- optional(namedParameter[String]("An optional parameter", "value", "optional"))
        } yield (required, nonRequired)

        assert(Parser.extractParameters(Seq("--required", "x", "--optional", "y"), spec))(isRight(equalTo((
          ("x", Some("y"))
          )))) &&
          assert(Parser.extractParameters(Seq("--required", "x"), spec))(isRight(equalTo(
            ("x", None)
          )))
      },

      test("optional simple parameter works") {
        val spec = for {
          required <- parameter[String]("A required parameter", "value")
          nonRequired <- optional(parameter[String]("An optional parameter", "value"))
        } yield (required, nonRequired)

        assert(Parser.extractParameters(Seq("x", "y"), spec))(isRight(equalTo(
          ("x", Some("y"))
        ))) &&
          assert(Parser.extractParameters(Seq("x"), spec))(isRight(equalTo(
            ("x", None)
          )))
      },

      test("optional multiple parameter blocks") {
        val spec = for {
          required <- namedParameter[String]("A required parameter", "value", "required")
          size <- optional {
            for {
              w <- namedParameter[Int]("Width", "integer", 'w', "width")
              h <- namedParameter[Int]("Height", "integer", 'h', "height")
            } yield (w, h)
          }
        } yield (required, size)

        assert(Parser.extractParameters(Seq("--required", "test", "-w", "10", "--height", "20"), spec))(isRight(equalTo(
          ("test", Some((10, 20)))
        ))) &&
          assert(Parser.extractParameters(Seq("--required", "test", "-w", "10"), spec))(failWithErrors(
            UnknownParameter("-w"), UnknownParameter("10")
          )) &&
          assert(Parser.extractParameters(Seq("--required", "test"), spec))(isRight(equalTo(
            ("test", None)
          )))
      },

      test("accept valid command") {
        assert(Parser.extractParameters(Seq("first", "--name", "test", "--password", "xxx"), specCommand)
        )(isRight(equalTo(
          (false, Left(("test", "xxx")))
        ))) &&
          assert(Parser.extractParameters(Seq("-v", "first", "--name", "test", "--password", "xxx"), specCommand)
          )(isRight(equalTo(
            (true, Left(("test", "xxx")))
          ))) &&
          assert(Parser.extractParameters(Seq("-v", "second"), specCommand)
          )(isRight(equalTo(
            (true, Right(false))
          )))
      },

      test("refuse invalid command") {
        assert(Parser.extractParameters(Seq("third", "--name", "test", "--password", "xxx"), specCommand))(failWithErrors(
          InvalidCommand("third", List("first", "second"))
        ))
      },

      test("does not accept parameters after command") {
        assert(Parser.extractParameters(Seq("first", "-v", "--name", "test", "--password", "xxx"), specCommand))(failWithErrors(
          UnknownParameter("-v")
        ))
      },

      test("custom failure with valid input") {
        assert(Parser.extractParameters(Seq("--name", "test"), specCustomFailure)
        )(isRight(equalTo("test")))
      },

      test("custom failure with invalid input") {
        assert(Parser.extractParameters(Seq.empty, specCustomFailure))(failWithErrors(
          CustomError("name was not defined")
        ))
      },

      suite("lift arbitrary functions to the parser")(
        test("success") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- lift("test", "ex1") {
                verbose.toString
              }
            } yield result
          )
          )(isRight(equalTo("true")))
        },

        test("is suspended") {
          val builder = new StringBuilder
          val spec = for {
            verbose <- flag("verbose", 'v')
            result <- lift("test", "ex1") {
              builder.append("hello");
              verbose.toString
            }
          } yield result

          val before = builder.toString()

          val result = Parser.extractParameters(
            Seq("-v"),
            spec
          )
          val after = builder.toString()

          assert(before)(equalTo("")) &&
            assert(result)(isRight(equalTo("true"))) &&
            assert(after)(not(equalTo(""))) // NOTE: not guaranteed that the effect runs only once
        },

        test("throwing exception") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- lift("test", "ex1") {
                throw new RuntimeException("lifted function fails")
              }
            } yield result
          ))(failWithErrors(CustomError("lifted function fails")))
        }
      ),

      suite("liftEither arbitrary functions to the parser")(
        test("success") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftEither[String, String]("test", "ex1") {
                Right(verbose.toString)
              }
            } yield result
          )
          )(isRight(equalTo("true")))
        },

        test("failure") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftEither("test", "ex1") {
                Left("lifted function fails")
              }
            } yield result
          ))(failWithErrors(CustomError("lifted function fails")))
        }
      ),

      suite("liftTry arbitrary functions to the parser")(
        test("success") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftTry("test", "ex1") {
                Success(verbose.toString)
              }
            } yield result
          )
          )(isRight(equalTo("true")))
        },

        test("failure") {
          assert(Parser.extractParameters(
            Seq("-v"),
            for {
              verbose <- flag("verbose", 'v')
              result <- liftTry("test", "ex1") {
                Failure(new RuntimeException("lifted function fails"))
              }
            } yield result
          ))(failWithErrors(CustomError("lifted function fails")))
        }
      )
    )

  private def failWithErrors[T](error0: ParserError, errorss: ParserError*): Assertion[Either[ParserFailure, T]] =
    isLeft(hasField("errors", (f: ParserFailure) => f.errors, equalTo(NonEmptyList(error0, errorss.toList))))

}
