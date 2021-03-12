package io.github.vigoo.clipp

import java.io.File
import java.nio.file.{Path, Paths}
import scala.util.Try

object parsers {

  implicit val stringParameterParser: ParameterParser[String] = new ParameterParser[String] {
    override def parse(value: String): Either[String, String] = Right(value)

    override def example: String = ""
  }

  implicit val intParameterParser: ParameterParser[Int] = new ParameterParser[Int] {
    override def parse(value: String): Either[String, Int] = Try(value.toInt).toEither.left.map(_.getMessage)

    override def example: Int = 0
  }

  implicit val doubleParameterParser: ParameterParser[Double] = new ParameterParser[Double] {
    override def parse(value: String): Either[String, Double] = Try(value.toDouble).toEither.left.map(_.getMessage)

    override def example: Double = 0.0
  }

  implicit val fileParameterParser: ParameterParser[File] = new ParameterParser[File] {
    override def parse(value: String): Either[String, File] = Right(new File(value))

    override def example: File = new File("x")
  }
}
