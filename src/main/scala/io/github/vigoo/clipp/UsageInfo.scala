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

import scala.collection.mutable

object UsageInfo {

  case class DescribedParameter(parameter: Parameter[_], isInOptionalBlock: Boolean)

  type Choice = Any
  type Choices = Map[Parameter[_], Choice]
  type MergedChoices = Map[Parameter[_], Set[Choice]]
  type ResultGraph = Graph[DescribedParameter, Choices]

  case class ExtractUsageInfoState(isInOptionalBlock: Boolean, last: Option[DescribedParameter], choices: Choices)

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

  def prettyPrint(usageDescription: ResultGraph): String = {
    val usageNodes = usageDescription.toNodes
    usageNodes.find(_.sourceNodes.isEmpty) match {
      case Some(start) =>
        val builder = mutable.StringBuilder.newBuilder

        prettyPrintNode(start, builder)

        builder.toString()
      case None =>
        "No usage info is available"
    }
  }

  private def prettyPrintNode(node: Node[DescribedParameter, Choices], builder: mutable.StringBuilder, prefix: String = ""): Unit = {
    builder.append(prefix)
    node.value.parameter match {
      case Flag(shortName, longNames, description) =>
        builder.append(s"flag $shortName ($longNames) $description\n") // TODO
      case NamedParameter(shortName, longNames, placeholder, description, _) =>
        builder.append(s"named parameter $shortName ($longNames) $placeholder $description\n") // TODO
      case SimpleParameter(placeholder, description, parameterParser) =>
        builder.append(s"simple parameter $placeholder $description\n") // TODO
      case Command(validCommands) =>
        builder.append(s"command $validCommands\n") // TODO
      case Optional(parameter) =>
        builder.append("optional\n") // TODO
    }

    val orderedTargetNodes = node.targetNodes.toVector
    val orderedMergedChoices = orderedTargetNodes.map(targetNode => mergeChoices(targetNode.labels))
    val orderedFilteredChoices = withoutSharedChoices(orderedMergedChoices)

    for (idx <- orderedTargetNodes.indices) {
      val targetNode = orderedTargetNodes(idx)
      if (orderedTargetNodes.size > 1) {
        val choices = orderedFilteredChoices(idx)

        builder.append(prefix)
        builder.append(s"When $choices:\n")
        prettyPrintNode(targetNode.to, builder, prefix + "  ")
      }
      else {
        prettyPrintNode(targetNode.to, builder, prefix)
      }
    }

    builder.append("\n")
  }

  private def withoutSharedChoices(mergedChoices: Vector[MergedChoices]): Vector[MergedChoices] = {
    if (mergedChoices.nonEmpty) {
      val sharedChoices =
        mergedChoices.map(_.toSet).reduce { (result, cs) =>
          result intersect cs
        }

      mergedChoices.map(cs => cs.toSet.diff(sharedChoices).toMap)
    } else {
      mergedChoices
    }
  }

  private def mergeChoices(choices: Set[Choices]): MergedChoices = {
    choices.foldLeft(Map.empty[Parameter[_], Set[Choice]]) { case (r1, cs) =>
        cs.foldLeft(r1) { case (r2, (param, choice)) =>
            r2.updated(param, r2.getOrElse(param, Set.empty) + choice)
        }
    }
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
