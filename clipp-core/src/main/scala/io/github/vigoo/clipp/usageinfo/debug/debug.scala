package io.github.vigoo.clipp.usageinfo

import java.io.{StringWriter, Writer}

import scala.collection.mutable

package object debug {

  implicit class GraphToDot[T, L](val graph: Graph[T, L]) {

    def dot(): String = {
      val writer = new StringWriter()
      writeDot(writer)
      writer.toString
    }

    def writeDot(writer: Writer): Unit = {
      writer.write("digraph G {\n")

      val nodeMap = mutable.Map.empty[T, String]
      var nodeCounter = 1

      def nameNode(node: T): String = {
        nodeMap.get(node) match {
          case Some(name) => name
          case None =>
            val name = s"node$nodeCounter"
            nodeMap.update(node, name)
            nodeCounter = nodeCounter + 1
            name
        }
      }

      for (edge <- graph.edges) {
        val from = nameNode(edge.from)
        val to = nameNode(edge.to)

        writer.write(s"""$from -> $to [label="${edge.labels.mkString(", ")}"];\n""")
      }

      for ((node, name) <- nodeMap) {
        writer.write(s"""$name [label="$node"]\n""")
      }

      writer.write("}\n")
      writer.flush()
    }
  }

}
