package io.github.vigoo.clipp.usageinfo

import java.util.UUID

import cats.data.{State, Writer}
import cats.free.Free
import cats.kernel.Monoid
import cats._
import cats.implicits._
import io.github.vigoo.clipp._
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._

object UsageInfoExtractor {

  sealed trait GraphNode

  case class DescribedParameter(parameter: Parameter[_], isInOptionalBlock: Boolean) extends GraphNode

  case class PathEnd(uniqueId: UUID) extends GraphNode

  type Choice = Any
  type Choices = Map[Parameter[_], Choice]
  type MergedChoices = Map[Parameter[_], Set[Choice]]
  type ResultGraph = Graph[GraphNode, Choices]

  case class ExtractUsageInfoState(isInOptionalBlock: Boolean, last: Option[GraphNode], choices: Choices)

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
        describedParameter = DescribedParameter(parameter, state.isInOptionalBlock)
        _ <- state.last match {
          case Some(last) => tell[UsageInfoExtractor, ResultGraph](Graph.edge(last, describedParameter, state.choices))
          case None => unit[UsageInfoExtractor]
        }
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state.copy(
          last = Some(describedParameter),
          choices = state.choices + (parameter -> choice)
        ))
      } yield ()

    def reset(): UsageInfoM[Unit] =
      for {
        state <- getState
        stop = PathEnd(UUID.randomUUID())
        _ <- state.last match {
          case Some(last) => tell[UsageInfoExtractor, ResultGraph](Graph.edge(last, stop, state.choices))
          case None => unit[UsageInfoExtractor]
        }
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state.copy(
          last = None,
          choices = Map.empty
        ))
      } yield ()

    def flag(flag: Flag): UsageInfoM[Boolean] = {
      for {
        enabled <- choice[Boolean](List(false, true))
        _ <- record(flag, enabled)
      } yield enabled
    }

    def namedParameter[T](namedParameter: NamedParameter[T], result: T): UsageInfoM[T] = {
      for {
        _ <- record(namedParameter, result)
      } yield result
    }

    def simpleParameter[T](simpleParameter: SimpleParameter[T], result: T): UsageInfoM[T] = {
      for {
        _ <- record(simpleParameter, result)
      } yield result
    }

    def command(command: Command): UsageInfoM[String] = {
      for {
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
