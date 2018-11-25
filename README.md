# clipp
[![Build Status](https://travis-ci.org/vigoo/clipp.svg?branch=master)](https://travis-ci.org/vigoo/clipp)
[![codecov](https://codecov.io/gh/vigoo/clipp/branch/master/graph/badge.svg)](https://codecov.io/gh/vigoo/clipp)
[![Apache 2 License License](http://img.shields.io/badge/license-APACHE2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Functional command line argument parser and usage info generator for Scala.

### The idea
The main difference between *clipp* and other similar libraries is that the parsing specification is monadic. 
This makes it very easy to use in a functional style without making compromises of the generated data structure.

Let's see the following example:

```scala
sealed trait TestCommand

case class Command1(name: String) extends TestCommand

case class Command2(x: Int, y: Int) extends TestCommand

case class TestParameters(verbose: Boolean,
                        input: File,
                        output: Option[File],
                        weight: Option[Double],
                        command: TestCommand)
                        
val paramSpec =
  for {
    _ <- metadata(programName = "example")

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
```

The semantics of these command line parser commands are the following:
- `flag` looks for the given flag *before the first command location* (if any), in case it finds one it **removes** it from the list of arguments and returns true.
- `namedParameter[T]` looks for a `--name value` pair of arguments *before the first command location* (if any), **removes both** from the list of arguments and parses the value with an instance of the `ParameterParser` type class
- `parameter[T]` takes the first argument that does not start with `-` *before the first command location* (if any) and **removes it from the list of arguments, then parses the value with an instance of the `ParameterParser` type class
- `optional` makes parser specification section optional
- `command` is a special parameter with a fix set of values, which is parsed by taking the first argument that does not start with `-` and **it drops all the arguments until the command** from the list of arguments.

The semantics of `command` strongly influences the set of CLI interfaces parseable by this library, but it is
a very important detail for the current implementation.    

## Usage info generation
The level of freedom the specification monad gives makes it hard to automatically generate usage info, 
but the library tries to implement a heuristics that is good in many common cases and also lets you 
customize it on multiple levels.


### Auto mode

Let's see a silly example of a more complex parser to see how it works:
```scala
  val spec = for {
    _ <- metadata(programName = "example", description = "An example created to show usage info support")
    withAge <- flag("With age", 'a')
    cmd <- command("a", "b", "c")
    result <- cmd match {
      case "a" =>
        for {
          x <- namedParameter[Double]("X", "value", 'x')
          y <- namedParameter[Double]("Y", "value", 'y')
        } yield 0
      case "b" =>
        for {
          name <- parameter[String] ("Name", "name")
          age <- if (withAge) { parameter[Int]("Age", placeholder = "age in years") } else { pure(0) }
        } yield 1
      case "c" =>
        for {
          x <- namedParameter[Double]("X", "value", 'x')
          y <- namedParameter[Double]("Y", "value", 'y')
          z <- namedParameter[Double]("Z", "value", 'z')
        } yield 2
    }
  } yield result
```

The output will be:
```plain
Usage: example [-a] [command] ...

An example created to show usage info support
  -a                                    With age
  <command>                             One of a, b, c

  When command is one of a, c:
    -x <value>                          X
    -y <value>                          Y

    When command is c:
      -z <value>                        Z

    When command is b:
      <name>                            Name

      When -a is true:
        <age in years>                  Age
```

The *usage info generator* executes the parser with multiple, automatically generated choices
in order to figure out the execution graph. Currently it only generates choices for:

- flags, trying *both true and false*
- commands, trying *all the valid commands*

### Customizing choices

All the syntax functions have variants with `withExplicitChoices = List[T]` parameters which turns
off the automatic branching and uses the given list of values to generate the usage info graph. By
providing a single value, the choice can be locked to a fix value.

### Manual mode

In very complex cases the pretty printer part of the library can be still used to display
customized information. In this case a custom list of `PrettyPrintCommand`s and an optional
`ParameterParserMetadata` can be provided to the `UsagePrettyPrinter`.

### Partially locked choices
In case of showing the usage info by reacting to bad user input, it is possible to use the state of the
parser up until the error to lock the *choices* to specific values. This has the same effect as
locking them to a particular value statically with the `withExplicitChoices = List(x)` syntax.

This can be used to display only relevant parts of the usage info, for example in sub-command
style cases.
