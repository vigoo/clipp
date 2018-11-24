# clipp
Functional command line argument parser and usage info generator for Scala.

**NOT PRODUCTION READY YET!** 

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

TODO: document this 