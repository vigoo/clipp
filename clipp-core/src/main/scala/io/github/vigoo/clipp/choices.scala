package io.github.vigoo.clipp

object choices {

  sealed trait Choice {
    def value: Any
  }

  object Choice {
    implicit val ordering: Ordering[Choice] = (x: Choice, y: Choice) => {
      (x, y) match {
        case (BooleanChoice(v1), BooleanChoice(v2)) => (if (v1) -1 else -2) - (if (v2) -1 else -2)
        case (c1: CommandChoice, c2: CommandChoice) => c1.valueIndex - c2.valueIndex
        case (_, _) => 0

      }
    }

    def isTotal(choices: List[Choice]): Boolean = {
      choices match {
        case List(BooleanChoice(v1), BooleanChoice(v2)) if v1 != v2 => true
        case CommandChoice(v1, validValues) :: rest =>
          val otherValues = rest.collect { case CommandChoice(v, vv) if validValues == vv => v }
          otherValues.length == rest.length && ((otherValues.toSet + v1) == validValues.toSet)
        case _ => false
      }
    }

    def participatesInOrdering(choice: Choice): Boolean = choice match {
      case CommandChoice(_, _) => true
      case _ => false
    }
  }

  final case class BooleanChoice(value: Boolean) extends Choice

  final case class CommandChoice(value: String, validValues: List[String]) extends Choice {
    val valueIndex: Int = validValues.indexOf(value)
  }

  final case class ArbitraryChoice(value: Any) extends Choice

  type Choices = Map[Parameter[_], Choice]
}
