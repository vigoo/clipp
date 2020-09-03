---
layout: docs
title: Parsers
---

# Writing custom parsers
It is possible to write custom parameter parsers by implementing the `ParameterParser` type class,
which has the following definition:

```scala
trait ParameterParser[T] {
  def parse(value: String): Either[String, T]
  def default: T
}
```

For example the built-in implementation for `Int` values looks like this:

```scala mdoc
import io.github.vigoo.clipp._
import scala.util._

object parsers {
  implicit val intParameterParser: ParameterParser[Int] = new ParameterParser[Int] {
    override def parse(value: String): Either[String, Int] = Try(value.toInt).toEither.left.map(_.getMessage)

    override def default: Int = 0
  }
}
```