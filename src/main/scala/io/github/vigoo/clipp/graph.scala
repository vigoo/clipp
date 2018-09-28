package io.github.vigoo.clipp

import cats.Monoid

case class Edge[T, L](from: T, to: T, labels: Set[L]) {
  override def toString: String = s"$from -[${labels.mkString(", ")}]-> $to"
}
case class Graph[T, L](edges: Set[Edge[T, L]])

object Graph {
  def edge[T, L](from: T, to: T, label: L): Graph[T, L] =
    Graph(Set(Edge(from, to, Set(label))))

  implicit def graphMonoid[T, L]: Monoid[Graph[T, L]] = new Monoid[Graph[T, L]] {
    override def empty = Graph(Set.empty)

    override def combine(x: Graph[T, L], y: Graph[T, L]) =
      Graph(x.edges.union(y.edges)) // TODO: combine labels too
  }
}
