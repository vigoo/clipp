package io.github.vigoo.clipp.usageinfo

import java.util.UUID

import cats.data.{State, Writer}
import cats.free.Free
import cats.kernel.Monoid
import cats._
import cats.implicits._
import io.github.vigoo.clipp._
import io.github.vigoo.clipp.choices._
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._

object UsageInfoExtractor {

  sealed trait GraphNode

  case class DescribedParameter(parameter: Parameter[_], isInOptionalBlock: Boolean) extends GraphNode

  case class PathEnd(uniqueId: UUID) extends GraphNode

  type MergedChoices = Map[Parameter[_], Set[Choice]]
  type ResultGraph = Graph[GraphNode, Choices]

  case class UsageDescription(resultGraph: ResultGraph, metadata: Option[ParameterParserMetadata])

  case class ExtractUsageInfoState(isInOptionalBlock: Boolean,
                                   last: Option[GraphNode],
                                   choices: Choices,
                                   fixedChoices: Choices,
                                   metadata: Option[ParameterParserMetadata])

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
      case SetMetadata(metadata) =>
        impl.setMetadata(metadata)
    }
  }

  def getUsageDescription[T](by: Free[Parameter, T], partialChoices: Choices = Map.empty): UsageDescription = {

    val initialState = ExtractUsageInfoState(
      isInOptionalBlock = false,
      last = None,
      choices = Map.empty,
      fixedChoices = partialChoices,
      metadata = None)
    val extractor = for {
      result <- by.foldMap(usageInfoExtractor)
      _ <- impl.reset()
    } yield result

    val (extractorResult, finalState) = extractor.runWriter.runChoose[List].runState(initialState).run
    val graphs = extractorResult.flatMap(_._2)
    val combined = Monoid.combineAll(graphs)

    UsageDescription(resultGraph = combined, metadata = finalState.metadata)
  }


  private object impl {
    private def getState: UsageInfoM[ExtractUsageInfoState] =
      get[UsageInfoExtractor, ExtractUsageInfoState]

    private def choice[T](param: Parameter[T], from: List[T]): UsageInfoM[T] = {
      for {
        state <- getState
        finalFrom = state.fixedChoices.get(param) match {
          case Some(choice) => List(choice.value.asInstanceOf[T])
          case None => from
        }
        value <- chooseFrom[UsageInfoExtractor, T](finalFrom)
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state) // restore state
      } yield value
    }

    private def record[T](parameter: Parameter[T], choice: Choice): UsageInfoM[Unit] =
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
        enabled <- choice[Boolean](flag, flag.explicitChoices.getOrElse(List(false, true)))
        _ <- record(flag, BooleanChoice(enabled))
      } yield enabled
    }

    def namedParameter[T](namedParameter: NamedParameter[T], result: T): UsageInfoM[T] = {
      for {
        value <- choice[T](namedParameter, namedParameter.explicitChoices.getOrElse(List(result)))
        _ <- record(namedParameter, ArbitraryChoice(value))
      } yield result
    }

    def simpleParameter[T](simpleParameter: SimpleParameter[T], result: T): UsageInfoM[T] = {
      for {
        value <- choice[T](simpleParameter, simpleParameter.explicitChoices.getOrElse(List(result)))
        _ <- record(simpleParameter, ArbitraryChoice(value))
      } yield result
    }

    def command(command: Command): UsageInfoM[String] = {
      for {
        choice <- choice[String](command, command.explicitChoices.getOrElse(command.validCommands))
        _ <- record(command, CommandChoice(choice, command.validCommands))
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

    def setMetadata(metadata: ParameterParserMetadata): UsageInfoM[Unit] = {
      for {
        state <- getState
        _ <- put[UsageInfoExtractor, ExtractUsageInfoState](state.copy(metadata = Some(metadata)))
      } yield ()
    }
  }

}
