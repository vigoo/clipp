---
layout: docs
title: Cats Effect
---

# Using with Cats-Effect

To use the Cats-Effect interface add the following dependency:

```scala
libraryDependencies += "io.github.vigoo" %% "clipp-cats-effect" % "0.4.0"
```

Example:
```scala mdoc:silent
import io.github.vigoo.clipp._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.catseffect._

import cats.effect._

object Test extends IOApp {
    override def run(args: List[String]): IO[ExitCode] = {
      val paramSpec = for {
        _ <- metadata("zio-test")
        x <- flag("test parameter", 'x')
      } yield x

      Clipp.parseOrDisplayUsageInfo(args, paramSpec, ExitCode.Error) { x =>
        IO(println(s"x was: $x")).map(_ => ExitCode.Success)
      }
    }
  }
```
