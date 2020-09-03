---
layout: docs
title: Cats Effect
---

# Using with ZIO
To use the ZIO interface add the following dependency:

```scala
libraryDependencies += "io.github.vigoo" %% "clipp-zio" % "0.4.0"
```

It is possible to directly call the ZIO interface wrapper, for example:
```scala mdoc:silent
import io.github.vigoo.clipp._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.zioapi._

import zio._

object Test1 extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val paramSpec = for {
      _ <- metadata("zio-test")
      x <- flag("test parameter", 'x')
    } yield x

    Clipp.parseOrDisplayUsageInfo(args, paramSpec, ExitCode.failure) { x =>
      console.putStrLn(s"x was: $x").as(ExitCode.success)      
    }.catchAll { _: ParserFailure => ZIO.succeed(ExitCode.failure) }
  }
} 
```

An even better alternative is to construct a `ZLayer` from the parameters:

```scala mdoc:silent
import io.github.vigoo.clipp._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.zioapi._
import io.github.vigoo.clipp.zioapi.config

import zio._

object Test2 extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val paramSpec = for {
      _ <- metadata("zio-test")
      x <- flag("test parameter", 'x')
    } yield x

    val clippConfig = config.fromArgsWithUsageInfo(args, paramSpec)
    val program = for {
        x <- config.parameters[Boolean]
        _ <- console.putStrLn(s"x was: $x")
    } yield ExitCode.success
    
    program
      .provideSomeLayer(clippConfig)
      .catchAll { _: ParserFailure => ZIO.succeed(ExitCode.failure) }    
  }
} 
```