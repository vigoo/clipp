package io.github.vigoo.clipp

import cats.data.{State, StateT, Writer, WriterT}
import cats._
import cats.free.Free
import cats.implicits._
import cats.instances.list.catsStdInstancesForList
import cats.kernel.Monoid
import io.github.vigoo.clipp.UsageInfo.{ExtractUsageInfoState, MergedChoices}
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._

import scala.annotation.tailrec
import scala.collection.immutable.Queue
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
        prettyPrint(preprocess(start, Set.empty).toList, 0, mutable.StringBuilder.newBuilder)
      case None =>
        "No usage info is available"
    }
  }

  sealed trait PrettyPrintCommand
  case class PrintNode(node: Node[DescribedParameter, Choices]) extends PrettyPrintCommand
  case class PrintChoice(choice: MergedChoices) extends PrettyPrintCommand
  case object StartBranch extends PrettyPrintCommand
  case object ExitBranch extends PrettyPrintCommand

  private val descriptionX = 40

  private def prettyPrintOptionsAndDesc(level: Int, options: String, description: String, builder: StringBuilder): Unit = {
    builder.append("  " * level)
    builder.append(options)
    val remainingSpace = descriptionX - (options.length + (level * 2))

    if (remainingSpace < 0) {
      builder.append('\n')
      builder.append(" " * descriptionX)
    } else {
      builder.append(" " * remainingSpace)
    }
    builder.append(description)
    builder.append('\n')
  }

  private def prettyPrintChoice(choice: (Parameter[_], Set[UsageInfo.Choice])): String = {

    val (param, choices) = choice

    val what =
      param match {
        case Flag(shortName, longNames, _) =>
          shortName.map("-" + _).getOrElse("--" + longNames.head)
        case NamedParameter(shortName, longNames, _, _, _) =>
          shortName.map("-" + _).getOrElse("--" + longNames.head)
        case SimpleParameter(placeholder, _, _) =>
          s"<$placeholder>"
        case Command(_) =>
          "command"
        case Optional(parameter) =>
          throw new IllegalStateException(s"Optionals should have been prefiltered")
      }

    val values = choices.mkString(", ")

    if (choices.size > 1) {
      s"$what is one of $values"
    } else {
      s"$what is $values"
    }
  }

  @tailrec
  private def prettyPrint(cmds: List[PrettyPrintCommand], level: Int, builder: StringBuilder): String =
    cmds match {
      case Nil => builder.toString
      case PrintNode(node) :: remaining =>
        node.value.parameter match {
          case Flag(shortName, longNames, description) =>
            val allOptions = shortName.map("-" + _).toList ::: longNames.map("--" + _).toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, builder)

          case NamedParameter(shortName, longNames, placeholder, description, _) =>
            val allOptions = shortName.map(c => s"-$c <$placeholder>").toList ::: longNames.map(n => s"--$n <$placeholder>").toList
            prettyPrintOptionsAndDesc(level, allOptions.mkString(", "), description, builder)

          case SimpleParameter(placeholder, description, _) =>
            prettyPrintOptionsAndDesc(level, s"<$placeholder>", description, builder)

          case Command(validCommands) =>
            prettyPrintOptionsAndDesc(level, s"<command>", s"One of ${validCommands.mkString(", ")}", builder)

          case Optional(parameter) =>
            throw new IllegalStateException(s"Optionals should have been prefiltered")
        }
        prettyPrint(remaining, level, builder)
      case PrintChoice(choice) :: remaining =>
        builder.append('\n')
        builder.append("  " * level)
        builder.append(s"When ${choice.map(prettyPrintChoice).mkString(", ")}:\n")
        prettyPrint(remaining, level, builder)
      case StartBranch :: remaining =>
        prettyPrint(remaining, level + 1, builder)
      case ExitBranch :: remaining =>
        prettyPrint(remaining, level - 1, builder)
    }

  def preprocess(node: Node[DescribedParameter, Choices],
                  joinPoints: Set[Node[DescribedParameter, Choices]]): Vector[PrettyPrintCommand] = {

    val orderedTargetNodes: Vector[TargetNode[DescribedParameter, Choices]] = node.targetNodes.toVector
    val orderedMergedChoices = orderedTargetNodes.map(targetNode => mergeChoices(targetNode.labels))
    val orderedFilteredChoices: Vector[MergedChoices] = withoutSharedChoices(orderedMergedChoices)

    if (orderedTargetNodes.size > 1) {
      // This is a branching point

      // TODO: this should be based on the longest branch (?)
      val to0 = orderedTargetNodes(0).to
      val choices0 = orderedFilteredChoices(0)

      val branch0 = preprocess(to0, joinPoints)
      val newJoinPoints = branch0.collect { case PrintNode(n) => n }.toSet

      val otherBranches = orderedTargetNodes.indices.toVector.tail.map { idx =>
        val to = orderedTargetNodes(idx).to
        val choices = orderedFilteredChoices(idx)

        (preprocess(to, newJoinPoints), choices)
      }

      val allBranches = ((branch0, choices0) +: otherBranches).map { case (branch, choices) =>
        PrintChoice(choices) +: StartBranch +: branch :+ ExitBranch
      }

      PrintNode(node) +: allBranches.flatten
    } else {
      // No branching, go to next
      orderedTargetNodes.headOption match {
        case Some(next) =>
          val nextNode = next.to
          if (!joinPoints.contains(nextNode)) {
            PrintNode(node) +: preprocess(nextNode, joinPoints)
          } else {
            Vector(PrintNode(node))
          }
        case None =>
          Vector(PrintNode(node))
      }
    }
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
