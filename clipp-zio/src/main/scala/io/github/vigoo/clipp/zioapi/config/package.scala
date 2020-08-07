package io.github.vigoo.clipp.zioapi

import cats.free.Free
import io.github.vigoo.clipp.{Parameter, ParserFailure}
import zio._
import zio.console.Console

package object config {
  type ClippConfig[T] = Has[ClippConfig.Service[T]]

  object ClippConfig {

    trait Service[T] {
      val parameters: T
    }
  }

  def parameters[T : Tag]: ZIO[ClippConfig[T], Nothing, T] = ZIO.access(_.get.parameters)

  def fromArgs[T : Tag](args: List[String], spec: Free[Parameter, T]): ZLayer[Console, ParserFailure, ClippConfig[T]] =
    Clipp.parseOrFail(args, spec)
      .map { p =>
        new ClippConfig.Service[T] {
          override val parameters: T = p
        }
      }
      .toLayer

  def fromArgsWithUsageInfo[T : Tag](args: List[String], spec: Free[Parameter, T]): ZLayer[Console, ParserFailure, ClippConfig[T]] =
    fromArgs(args, spec)
      .tapError { parserFailure: ParserFailure =>
        Clipp.displayErrorsAndUsageInfo(spec)(parserFailure)
      }
}
