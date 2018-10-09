package io.github.vigoo.clipp

import cats.Monoid

import scala.collection.mutable

case class SourceNode[T, L](from: Node[T, L], labels: Set[L])
case class TargetNode[T, L](to: Node[T, L], labels: Set[L])

class Node[T, L](val value: T) {
  private val sources: mutable.Map[Node[T, L], Set[L]] = mutable.Map.empty
  private val targets: mutable.Map[Node[T, L], Set[L]] = mutable.Map.empty

  def sourceNodes: Set[SourceNode[T, L]] =
    sources.map { case (node, labels) => SourceNode(node, labels) }.toSet

  def targetNodes: Set[TargetNode[T, L]] =
    targets.map { case (node, labels) => TargetNode(node, labels) }.toSet

  def addTarget(targetNode: Node[T, L], labels: Set[L]): Unit =
    targets.update(
      targetNode,
      targets.getOrElseUpdate(targetNode, Set.empty) union labels
    )

  def addSource(sourceNode: Node[T, L], labels: Set[L]): Unit =
    sources.update(
      sourceNode,
      sources.getOrElseUpdate(sourceNode, Set.empty) union labels
    )

  override def toString: String =
    s"<$value>"
}


case class Edge[T, L](from: T, to: T, labels: Set[L]) {
  override def toString: String = s"$from -[${labels.mkString(", ")}]-> $to"
}

case class Graph[T, L](edges: Set[Edge[T, L]]) {
  def toNodes: Set[Node[T, L]] = {
    val mapping: mutable.Map[T, Node[T, L]] = mutable.Map.empty

    for (edge <- edges) {
      val sourceNode = mapping.getOrElseUpdate(edge.from, new Node(edge.from))
      val targetNode = mapping.getOrElseUpdate(edge.to, new Node(edge.to))

      sourceNode.addTarget(targetNode, edge.labels)
      targetNode.addSource(sourceNode, edge.labels)
    }

    mapping.values.toSet
  }
}

object Graph {
  def edge[T, L](from: T, to: T, label: L): Graph[T, L] =
    Graph(Set(Edge(from, to, Set(label))))

  implicit def graphMonoid[T, L]: Monoid[Graph[T, L]] = new Monoid[Graph[T, L]] {
    override def empty = Graph(Set.empty)

    override def combine(x: Graph[T, L], y: Graph[T, L]) =
      Graph(x.edges.union(y.edges))
  }

}
