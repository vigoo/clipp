---
layout: docs
title: Usage info
---

# Usage info generation
The level of freedom the monadic structure gives makes it hard to automatically generate usage info,
but the library implements a heuristics that is good in most of the common cases, and also allows some
customization.

## Automatic mode
In automatic mode, the library introspects the parameter parser by running it with different 
automatically generated choices in order to figure out the execution graph.

These automatically generated choices are for:

- flags, trying *both true and false*
- commands, trying *all the valid commands*

Let's see how the usage info generated for the 4th example in the [getting started page](index.html) 
look like!

```scala mdoc:invisible
import java.io.File

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._

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

```scala mdoc:silent
import io.github.vigoo.clipp.usageinfo._

val usageGraph = UsageInfoExtractor.getUsageDescription(paramSpec4)

```

```scala mdoc
val usageInfo = UsagePrettyPrinter.prettyPrint(usageGraph)
```

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