package io.github.vigoo.clipp.usageinfo

import io.github.vigoo.clipp._
import io.github.vigoo.clipp.usageinfo.UsageInfoExtractor._

object UsageInfo {

  sealed trait PrettyPrintCommand

  case class PrintNode(param: DescribedParameter) extends PrettyPrintCommand

  case class PrintChoice(choice: MergedChoices) extends PrettyPrintCommand

  case object StartBranch extends PrettyPrintCommand

  case object ExitBranch extends PrettyPrintCommand

  // TODO: input choices (so for example fixed command choices filter the usage info)
  def generateUsageInfo(usageDescription: UsageDescription): Either[String, Vector[PrettyPrintCommand]] = {
    val usageNodes = usageDescription.resultGraph.toNodes
    usageNodes.find(_.sourceNodes.isEmpty) match {
      case Some(start) =>
        Right(generateUsageInfo(start))
      case None =>
        Left("No usage info is available")
    }
  }

  def generateUsageInfo(node: Node[GraphNode, Choices]): Vector[PrettyPrintCommand] = {
    node.value match {
      case PathEnd(_) => Vector.empty
      case describedParameter: DescribedParameter =>
        val orderedTargetNodes: Vector[TargetNode[GraphNode, Choices]] = node.targetNodes.toVector.sortWith(sortByChoices)
        val orderedMergedChoices = orderedTargetNodes.map(targetNode => withoutTotalChoices(mergeChoices(targetNode.labels)))
        val orderedFilteredChoices: Vector[MergedChoices] = withoutSharedChoices(orderedMergedChoices)

        if (orderedTargetNodes.size > 1) {
          // This is a branching point

          val branches = orderedTargetNodes.indices.toVector.map { idx =>
            val to = orderedTargetNodes(idx).to
            val choices = orderedFilteredChoices(idx)

            (generateUsageInfo(to), choices)
          }

          val shortestLength = branches.map { case (branch, _) => branch.length }.min
          val dropCount = (0 until shortestLength).dropWhile { idx =>
            branches.map { case (branch, _) => branch(branch.length - idx - 1) }.distinct.length == 1
          }.headOption.getOrElse(shortestLength)

          val reducedBranches = branches
            .map { case (branch, choices) => branch.dropRight(dropCount) -> choices }
            .filter { case (branch, _) => branch.nonEmpty }

          val commonTail = branches.head._1.takeRight(dropCount)

          val allBranches = reducedBranches.map { case (branch, choices) =>
            PrintChoice(choices) +: StartBranch +: branch :+ ExitBranch
          }

          PrintNode(describedParameter) +: (allBranches.flatten ++ commonTail)
        } else {
          // No branching, go to next
          orderedTargetNodes.headOption match {
            case Some(next) =>
              val nextNode = next.to
              PrintNode(describedParameter) +: generateUsageInfo(nextNode)
            case None =>
              Vector(PrintNode(describedParameter))
          }
        }
    }
  }

  private def sortByChoices(a: TargetNode[GraphNode, Choices], b: TargetNode[GraphNode, Choices]): Boolean = {
    val aList = a.labels.flatMap(_.values).filter(Choice.participatesInOrdering).toList
    val bList = b.labels.flatMap(_.values).filter(Choice.participatesInOrdering).toList

    if (aList.isEmpty) {
      true
    } else if (bList.isEmpty) {
      false
    } else {
      implicitly[Ordering[Choice]].lt(
        aList.min,
        bList.min
      )
    }
  }

  private def withoutTotalChoices(mergedChoices: MergedChoices): MergedChoices = {
    mergedChoices.filter { case (param, choices) =>
      !Choice.isTotal(choices.toList)
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
}
