package io.github.vigoo.clipp

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.util.Try

object parsers extends VersionSpecificParsers {

  implicit val charParameterParser: ParameterParser[Char] = new ParameterParser[Char] {
    override def parse(value: String): Either[String, Char] =
      if (value.length == 1)
        Right(value(0))
      else
        Left("Not a single character")

    override def example: Char = '?'
  }


  implicit val stringParameterParser: ParameterParser[String] = new ParameterParser[String] {
    override def parse(value: String): Either[String, String] = Right(value)

    override def example: String = ""
  }

  implicit val byteParameterParser: ParameterParser[Byte] = new ParameterParser[Byte] {
    override def parse(value: String): Either[String, Byte] = Try(value.toByte).toEither.left.map(_.getMessage)

    override def example: Byte = 0
  }

  implicit val shortParameterParser: ParameterParser[Short] = new ParameterParser[Short] {
    override def parse(value: String): Either[String, Short] = Try(value.toShort).toEither.left.map(_.getMessage)

    override def example: Short = 0
  }

  implicit val intParameterParser: ParameterParser[Int] = new ParameterParser[Int] {
    override def parse(value: String): Either[String, Int] = Try(value.toInt).toEither.left.map(_.getMessage)

    override def example: Int = 0
  }

  implicit val longParameterParser: ParameterParser[Long] = new ParameterParser[Long] {
    override def parse(value: String): Either[String, Long] = Try(value.toLong).toEither.left.map(_.getMessage)

    override def example: Long = 0L
  }

  implicit val floatParameterParser: ParameterParser[Float] = new ParameterParser[Float] {
    override def parse(value: String): Either[String, Float] = Try(value.toFloat).toEither.left.map(_.getMessage)

    override def example: Float = 0.0f
  }

  implicit val doubleParameterParser: ParameterParser[Double] = new ParameterParser[Double] {
    override def parse(value: String): Either[String, Double] = Try(value.toDouble).toEither.left.map(_.getMessage)

    override def example: Double = 0.0
  }

  implicit val uuidParameterParser: ParameterParser[UUID] = new ParameterParser[UUID] {
    override def parse(value: String): Either[String, UUID] = Try(UUID.fromString(value)).toEither.left.map(_.getMessage)

    override def example: UUID = UUID.randomUUID()
  }

  implicit val fileParameterParser: ParameterParser[File] = new ParameterParser[File] {
    override def parse(value: String): Either[String, File] = Right(new File(value))

    override def example: File = new File("x")
  }

  implicit val pathParameterParser: ParameterParser[Path] = new ParameterParser[Path] {
    override def parse(value: String): Either[String, Path] = Try(Paths.get(value)).toEither.left.map(_.getMessage)

    override def example: Path = Paths.get("x")
  }
}
