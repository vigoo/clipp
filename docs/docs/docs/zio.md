---
layout: docs
title: ZIO
---

# Using with ZIO 1.x
To use the ZIO interface add the following dependency:

```scala
libraryDependencies += "io.github.vigoo" %% "clipp-zio" % "0.6.2"
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
      console.putStrLn(s"x was: $x").ignore.as(ExitCode.success)
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

import zio._

object Test2 extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val paramSpec = for {
      _ <- metadata("zio-test")
      x <- flag("test parameter", 'x')
    } yield x

    val clippConfig = parametersFromArgs(args, paramSpec).printUsageInfoOnFailure
    val program = for {
        x <- parameters[Boolean]
        _ <- console.putStrLn(s"x was: $x").ignore
    } yield ExitCode.success
    
    program
      .provideSomeLayer(clippConfig)
      .catchAll { _: ParserFailure => ZIO.succeed(ExitCode.failure) }    
  }
} 
```

There is support for lifting ZIO effects with arbitrary environment requirements into the parameter parser. 
To use that, you either have to have an implicit ZIO `Runtime` or use the `effectfulParametersFromArgs` layer 
constructor:

```scala mdoc
import zio.clock._

effectfulParametersFromArgs[Clock, String](List.empty) { p =>
  p.liftURIO("the current instant", "2021-03-12T12:13:12Z") {
    instant.map(_.toString)
  }
}
```

# Using with ZIO 2.x
ZIO 2 support is currently published separately for snapshot versions of ZIO. Once ZIO 2 is released there will be only
one clipp-zio artifact maintained.

```scala
libraryDependencies += "io.github.vigoo" %% "clipp-zio-2" % "0.6.2"
```

The only difference currently is that instead of providing the list of arguments as a parameter, Clipp takes it from the 
`ZIOAppArgs` layer:

```scala
import io.github.vigoo.clipp._
import io.github.vigoo.clipp.parsers._
import io.github.vigoo.clipp.syntax._
import io.github.vigoo.clipp.zioapi._

import zio._

object Test2 extends ZIOApp {
  def run = {
    val paramSpec = for {
      _ <- metadata("zio-test")
      x <- flag("test parameter", 'x')
    } yield x

    val clippConfig = parametersFromArgs(paramSpec).printUsageInfoOnFailure
    val program = for {
        x <- parameters[Boolean]
        _ <- console.putStrLn(s"x was: $x").ignore
    } yield ExitCode.success
    
    program
      .injectCustom(clippConfig)
      .catchAll { _: ParserFailure => ZIO.succeed(ExitCode.failure) }    
  }
} 
```