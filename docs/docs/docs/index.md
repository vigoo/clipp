---
layout: docs
title: Getting started
---

# Getting started with clipp
Clipp lets you describe an immutable specification of how to turn command line arguments (a sequence of strings) into
an application-specific data structure. 

The specification is monadic, which makes it very easy to express sub-command behavior, as the parsing steps can
depend on a previously parsed value.

Let's see a simple example first:

```scala mdoc:silent
import java.io.File

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._

case class Parameters1(inputUrl: String,
                       outputFile: File,
                       verbose: Boolean)

val paramSpec1 = 
  for {
    _ <- metadata(programName = "Example 1")
 
    inputUrl <- parameter[String]("URL to download", "url")
    outputFile <- parameter[File]("Target file", "file")
    
    verbose <- flag("Verbose output", 'v', "verbose")
  } yield Parameters1(inputUrl, outputFile, verbose)
```
 
This takes two arguments in order, optionally with a `-v` or `--verbose` flag which can be in any location (see the 
exact semantics below), for example:

```
app -v http://something.to.download /tmp/to 
```

By using _named parameters_, the order of them does not matter anymore:

```scala mdoc:silent
val paramSpec2 = 
  for {
    _ <- metadata(programName = "Example 2")
 
    inputUrl <- namedParameter[String]("URL to download", "url", "input")
    outputFile <- namedParameter[File]("Target file", "file", "output")
    
    verbose <- flag("Verbose output", 'v', "verbose")
  } yield Parameters1(inputUrl, outputFile, verbose)
```

These can be specified in any order like:

```
app --output /tmp/to --verbose --input http://something.to.download 
```

We can use the `optinal` modifier to mark parts of the parser optional, making their result of type `Option[T]`. We
can for example modify the previous example to make the output optional (and print the downloaded data to the console
if it's not there):

```scala mdoc:silent
case class Parameters3(inputUrl: String,
                       outputFile: Option[File],
                       verbose: Boolean)

val paramSpec3 = 
  for {
    _ <- metadata(programName = "Example 3")
 
    inputUrl <- namedParameter[String]("URL to download", "url", "input")
    outputFile <- optional { namedParameter[File]("Target file", "file", "output") }
    
    verbose <- flag("Verbose output", 'v', "verbose")
  } yield Parameters3(inputUrl, outputFile, verbose)
```

### Commands
Support for _commands_ is a primary feature of _clipp_. The idea is that at a given point in the sequence of command
line arguments, a command selects the mode the application will operate in, and it selects possible parameters 
accepted after it. It is possible to create a hierarchy of commands. Think of `aws-cli` as an example.

Because the specification is monadic, it is very convenient to express this kind of behavior:

```scala mdoc:silent
sealed trait Subcommand
case class Create(name: String) extends Subcommand
case class Delete(id: Int) extends Subcommand

sealed trait Command
case class First(input: String) extends Command
case class Second(val1: Int, val2: Option[Int]) extends Command
case class Third(interactive: Boolean, subcommand: Subcommand) extends Command

case class Parameters4(verbose: Boolean,
                       command: Command)

val paramSpec4 =
  for {
    _ <- metadata(programName = "Example 4")

    verbose <- flag("Verbose output", 'v', "verbose")
    commandName <- command("first", "second", "third")
    command <- commandName match {
      case "first" =>
        for {
          input <- namedParameter[String]("Input value", "value", "input")
        } yield First(input)
      case "second" =>
        for {
          val1 <- namedParameter[Int]("First input value", "value", "val1")
          val2 <- optional { namedParameter[Int]("Second input value", "value", "val2") }
        } yield Second(val1, val2)
      case "third" =>
        for {
          interactive <- flag("Interactive mode", "interactive")
          subcommandName <- command("create", "delete")
          subcommand <- subcommandName match {
            case "create" => parameter[String]("Name of the thing to create", "name").map(Create(_))
            case "delete" => parameter[Int]("Id of the thing to delete", "id").map(Delete(_))
          }
        } yield Third(interactive, subcommand)
    }
  } yield Parameters4(verbose, command)
```   

### Semantics
The semantics of these parsing commands are the following:

- `flag` looks for the given flag *before the first command location* (if any), in case it finds one it **removes** it from the list of arguments and returns true.
- `namedParameter[T]` looks for a `--name value` pair of arguments *before the first command location* (if any), **removes both** from the list of arguments and parses the value with an instance of the `ParameterParser` type class
- `parameter[T]` takes the first argument that does not start with `-` *before the first command location* (if any) and **removes it from the list of arguments, then parses the value with an instance of the `ParameterParser` type class
- `optional` makes parser specification section optional
- `command` is a special parameter with a fix set of values, which is parsed by taking the first argument that does not start with `-` and **it drops all the arguments until the command** from the list of arguments.

The semantics of `command` strongly influences the set of CLI interfaces parseable by this library, but it is
a very important detail for the current implementation.    
