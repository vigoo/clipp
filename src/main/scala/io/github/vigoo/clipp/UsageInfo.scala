package io.github.vigoo.clipp

import cats.data.{State, StateT, Writer, WriterT}
import cats._
import cats.free.Free
import cats.implicits._
import cats.instances.list.catsStdInstancesForList
import cats.kernel.Monoid
import io.github.vigoo.clipp.UsageInfo.ExtractUsageInfoState
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._

object UsageInfo {


  type Choice = Any
  type Choices = Map[Parameter[_], Choice]
  type ResultGraph = Graph[Parameter[_], Choices]

  case class ExtractUsageInfoState(isInOptionalBlock: Boolean, last: Option[Parameter[_]], choices: Choices)

  private type UsageInfoExtractor = Fx.fx3[Choose, Writer[ResultGraph, ?], State[ExtractUsageInfoState, ?]]
  private type UsageInfoM[A] = Eff[UsageInfoExtractor, A]

  private val usageInfoExtractor: Parameter ~> UsageInfoM = new (Parameter ~> UsageInfoM) {
    override def apply[A](fa: Parameter[A]): UsageInfoM[A] = fa match {
      case flag: Flag =>
        impl.flag(flag)
      case namedParameter: NamedParameter[_] =>
        impl.namedParameter(namedParameter, namedParameter.parameterParser.default)
      case simpleParameter: SimpleParameter[_] =>
        impl.simpleParameter(simpleParameter, simpleParameter.parameterParser.default)
      case command: Command =>
        impl.command(command)
      case Optional(parameter) =>
        impl.optional(parameter)
    }
  }

  def getUsageDescription[T](by: Free[Parameter, T]): ResultGraph = {

    val initialState = ExtractUsageInfoState(isInOptionalBlock = false, last = None, choices = Map.empty)
    val extractor = for {
      result <- by.foldMap(usageInfoExtractor)
      _ <- impl.reset()
    } yield result

    val extractorResult = extractor.runWriter.runChoose[List].evalState(initialState).run
    val graphs = extractorResult.flatMap(_._2)
    val combined = Monoid.combineAll(graphs)

    combined
  }

  private object impl {
    private def getState: UsageInfoM[ExtractUsageInfoState] =
      get[UsageInfoExtractor, ExtractUsageInfoState]

    private def choice[T](from: List[T]): UsageInfoM[T] = {
      for {
        state <- getState
        value <- chooseFrom[UsageInfoExtractor, T](from)
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state) // restore state
      } yield value
    }

    private def record[T](parameter: Parameter[T], choice: T): UsageInfoM[Unit] =
      for {
        state <- getState
        _ <- state.last match {
          case Some(last) => tell[UsageInfoExtractor, ResultGraph](Graph.edge(last, parameter, state.choices))
          case None => unit[UsageInfoExtractor]
        }
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state.copy(
          last = Some(parameter),
          choices = state.choices + (parameter -> choice)
        ))
      } yield ()

    def reset(): UsageInfoM[Unit] =
      modify[UsageInfoExtractor, ExtractUsageInfoState](_.copy(
        last = None,
        choices = Map.empty
      ))

    def flag(flag: Flag): UsageInfoM[Boolean] = {
      for {
        state <- getState
        enabled <- choice[Boolean](List(false, true))
        _ <- record(flag, enabled)
      } yield enabled
    }

    def namedParameter[T](namedParameter: NamedParameter[T], result: T): UsageInfoM[T] = {
      for {
        state <- getState
        _ <- record(namedParameter, result)
      } yield result
    }

    def simpleParameter[T](simpleParameter: SimpleParameter[T], result: T): UsageInfoM[T] = {
      for {
        state <- getState
        _ <- record(simpleParameter, result)
      } yield result
    }

    def command(command: Command): UsageInfoM[String] = {
      for {
        state <- getState
        choice <- choice[String](command.validCommands.toList)
        _ <- record(command, choice)
      } yield choice
    }

    def optional[T](block: Free[Parameter, T]): UsageInfoM[Option[T]] = {
      getState.flatMap { state =>
        val extractor = block.foldMap(usageInfoExtractor)
        val (extractorResult, finalState): (List[(T, List[ResultGraph])], ExtractUsageInfoState) =
          extractor.runWriter.runChoose[List].runState(state.copy(isInOptionalBlock = true)).run

        val graphs = extractorResult.flatMap(_._2)
        val combined = Monoid.combineAll(graphs)

        for {
          _ <- tell[UsageInfoExtractor, ResultGraph](combined)
          _ <- put[UsageInfoExtractor, ExtractUsageInfoState](finalState.copy(isInOptionalBlock = false))
        } yield None
      }
    }
  }

}
