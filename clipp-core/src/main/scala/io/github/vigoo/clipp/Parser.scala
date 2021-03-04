package io.github.vigoo.clipp

import cats.data._
import cats.free.Free
import cats._
import cats.syntax.all._
import io.github.vigoo.clipp.choices.{ArbitraryChoice, BooleanChoice, Choices, CommandChoice}
import io.github.vigoo.clipp.errors._

object Parser {
  case class CommandLocation(position: Int, value: String)
  case class PositionedParameter(originalPosition: Int, value: String)

  case class ExtractParametersState(nonParsedArguments: Vector[PositionedParameter],
                                    remainingCommandPositions: List[CommandLocation],
                                    recordedChoices: Choices) {
    def isBeforeNextCommand(positionedParameter: PositionedParameter): Boolean = {
      remainingCommandPositions.headOption match {
        case Some(CommandLocation(position, _)) =>
          positionedParameter.originalPosition < position
        case None =>
          true
      }
    }
  }
  private type ExtractStateM[A] = EitherT[State[ExtractParametersState, ?], NonEmptyList[ParserError], A]
  private type CommandLocatorM[A] = WriterT[ExtractStateM, List[CommandLocation], A]

  private val commandLocator: Parameter ~> CommandLocatorM = new (Parameter ~> CommandLocatorM) {
    private def lift[A](f: ExtractStateM[A]): CommandLocatorM[A] =
      WriterT.liftF[ExtractStateM, List[CommandLocation], A](f)

    private def tell(commandLocation: CommandLocation): CommandLocatorM[Unit] =
      WriterT.tell(List(commandLocation))

    override def apply[A](fa: Parameter[A]): CommandLocatorM[A] = fa match {
      case flag: Flag =>
        lift(impl.flag(flag))
      case namedParam: NamedParameter[_] =>
        lift(impl.namedParameter(namedParam))
      case simpleParam: SimpleParameter[_] =>
        lift(impl.simpleParameter(simpleParam))
      case cmd: Command =>
        lift(impl.command(cmd, validateCommandLocations = false)).flatMap { pp =>
          tell(CommandLocation(pp.originalPosition, pp.value))
            .map(_ => pp.value)
        }
      case Optional(parameter) =>
        lift(impl.optional(parameter))
      case SetMetadata(metadata) =>
        lift(impl.setMetadata(metadata))
      case Fail(message) =>
        lift(impl.fail(message))
    }
  }

  private val parameterExtractor: Parameter ~> ExtractStateM = new (Parameter ~> ExtractStateM) {
    override def apply[A](fa: Parameter[A]): ExtractStateM[A] = fa match {
      case flag: Flag =>
        impl.flag(flag)
      case namedParam: NamedParameter[_] =>
        impl.namedParameter(namedParam)
      case simpleParam: SimpleParameter[_] =>
        impl.simpleParameter(simpleParam)
      case cmd: Command =>
        impl.command(cmd, validateCommandLocations = true).map(_.value)
      case Optional(parameter) =>
        impl.optional(parameter)
      case SetMetadata(metadata) =>
        impl.setMetadata(metadata)
      case Fail(message) =>
        impl.fail(message)
    }
  }

  def extractParameters[T](from: Seq[String], by: Free[Parameter, T]): Either[ParserFailure, T] = {
    val initialState = ExtractParametersState(
      nonParsedArguments = from.toVector.zipWithIndex.map { case (v, i) => PositionedParameter(i, v) },
      remainingCommandPositions = List.empty,
      recordedChoices = Map.empty)

    val (preprocessorFinalState, preprocessResult) = by.foldMap(commandLocator).run.value.run(initialState).value
    preprocessResult match {
      case Right((commandLocations, _)) =>
        val preprocessedState = initialState.copy(remainingCommandPositions = commandLocations)

        val (finalState, result) = by.foldMap(parameterExtractor).value.run(preprocessedState).value
        val partialChoices = finalState.recordedChoices

        result match {
          case Left(errors) =>
            Left(ParserFailure(errors, partialChoices))
          case Right(value) =>
            val unprocessedParameters = NonEmptyList.fromList(finalState.nonParsedArguments.map(_.value).toList)
            unprocessedParameters match {
              case Some(params) =>
                Either.left(ParserFailure(params.map(UnknownParameter.apply), partialChoices))
              case None =>
                Right(value)
            }
        }
      case Left(errors) =>
        Left(ParserFailure(errors, preprocessorFinalState.recordedChoices))
    }
  }

  private object impl {
    private def failWith[T](parserError: ParserError): ExtractStateM[T] =
      EitherT.leftT(NonEmptyList(parserError, List.empty))

    private def getState: ExtractStateM[ExtractParametersState] =
      EitherT.right(State.get[ExtractParametersState])

    private def setState(newState: ExtractParametersState): ExtractStateM[Unit] =
      EitherT.right(State.set(newState))

    private def possibleVariants(shortName: Option[Char], longNames: Set[String]): Set[String] = {
      longNames.map("--" + _)
        .union(shortName.map(ch => Set("-" + ch)).getOrElse(Set.empty))
    }

    private def parseString[T](parser: ParameterParser[T], string: String): ExtractStateM[T] =
      parser.parse(string) match {
        case Left(error) => failWith(FailedToParseValue(error, string))
        case Right(value) => EitherT.pure(value)
      }

    private def pure[T](value: T): ExtractStateM[T] =
      EitherT.pure(value)

    def flag(flag: Flag): ExtractStateM[Boolean] = {
      for {
        state <- getState
        variants = possibleVariants(flag.shortName, flag.longNames)
        index = variants
          .map { variant =>
            state.nonParsedArguments
              .filter(state.isBeforeNextCommand)
              .map(_.value).indexOf(variant) }
          .find(_ >= 0)
        _ <- setState(
          index match {
            case Some(idx) =>
              state.copy(
                nonParsedArguments = state.nonParsedArguments.patch(idx, Seq.empty, 1),
                recordedChoices = state.recordedChoices.updated(flag, BooleanChoice(true))
              )
            case None =>
              state.copy(
                recordedChoices = state.recordedChoices.updated(flag, BooleanChoice(false))
              )
          }
        )
      } yield index.isDefined
    }

    private def getNamedValueFromIndex(variants: Set[String], index: Option[Int]): ExtractStateM[String] =
      getState.flatMap { state =>
        index match {
          case Some(idx) =>
            state.nonParsedArguments.map(_.value).get(idx + 1) match {
              case Some(value) =>
                EitherT.pure(value)
              case None =>
                failWith[String](MissingValueForNamedParameter(state.nonParsedArguments(idx).value))
            }
          case None =>
            failWith[String](MissingNamedParameter(variants))
        }
      }

    def namedParameter[T](namedParam: NamedParameter[T]): ExtractStateM[T] = {
      for {
        // TODO: merge common code with other impls
        state <- getState
        variants = possibleVariants(namedParam.shortName, namedParam.longNames)
        index = variants
          .map { variant =>
            state.nonParsedArguments
              .filter(state.isBeforeNextCommand)
              .map(_.value).indexOf(variant) }
          .find(_ >= 0)
        stringResult <- getNamedValueFromIndex(variants, index)
        result <- parseString(namedParam.parameterParser, stringResult)
        _ <- setState(
          index match {
            case Some(idx) =>
              state.copy(
                nonParsedArguments = state.nonParsedArguments.patch(idx, Seq.empty, 2),
                recordedChoices = state.recordedChoices.updated(namedParam, ArbitraryChoice(result))
              )
            case None =>
              state
          }
        )
      } yield result
    }

    def simpleParameter[T](simpleParam: SimpleParameter[T]): ExtractStateM[T] = {
      for {
        state <- getState
        firstMatch = state.nonParsedArguments.map(_.value).find(p => !p.startsWith("-"))
        result <- firstMatch match {
          case Some(value) => parseString(simpleParam.parameterParser, value)
          case None => failWith(MissingSimpleParameter(simpleParam.placeholder))
        }
        _ <- setState(
          firstMatch match {
            case Some(value) =>
              val idx = state.nonParsedArguments.map(_.value).indexOf(value)
              state.copy(
                nonParsedArguments = state.nonParsedArguments.patch(idx, Seq.empty, 1),
                recordedChoices = state.recordedChoices.updated(simpleParam, ArbitraryChoice(result))
              )
            case None =>
              state
          }
        )
      } yield result
    }

    def command(cmd: Command, validateCommandLocations: Boolean): ExtractStateM[PositionedParameter] = {
      for {
        state <- getState
        firstMatch = state.nonParsedArguments.find { case PositionedParameter(_, p) => !p.startsWith("-") }
        result <- firstMatch match {
          case Some(positionedParameter) =>
            if (validateCommandLocations) {
              state.remainingCommandPositions.headOption match {
                case Some(CommandLocation(position, value))
                  if position == positionedParameter.originalPosition &&
                     value == positionedParameter.value =>

                  if (cmd.validCommands.contains(positionedParameter.value)) {
                    pure(positionedParameter)
                  } else {
                    failWith(InvalidCommand(positionedParameter.value, cmd.validCommands))
                  }
                case _ =>
                  failWith(CommandPositionIsNotStatic(cmd.validCommands))
              }
            } else {
              if (cmd.validCommands.contains(positionedParameter.value)) {
                pure(positionedParameter)
              } else {
                failWith(InvalidCommand(positionedParameter.value, cmd.validCommands))
              }
            }
          case None =>
            failWith(MissingCommand(cmd.validCommands))
        }
        _ <- setState(
          firstMatch match {
            case Some(PositionedParameter(_, value)) =>
              val idx = state.nonParsedArguments.map(_.value).indexOf(value)
              state.copy(
                nonParsedArguments = state.nonParsedArguments.drop(idx + 1),
                remainingCommandPositions = state.remainingCommandPositions.drop(1),
                recordedChoices = state.recordedChoices.updated(cmd, CommandChoice(value, cmd.validCommands))
              )
            case None =>
              state
          }
        )
      } yield result
    }

    def optional[T](parameter: Free[Parameter, T]): ExtractStateM[Option[T]] = {
      for {
        state <- getState
        (resultState, result) = parameter.foldMap(parameterExtractor).value.run(state).value
        finalResult = result match {
          case Left(_) => None
          case Right(value) => Some(value)
        }
        _ <- setState(
          finalResult match {
            case None => state
            case Some(_) => resultState
          }
        )
      } yield finalResult
    }

    def setMetadata(metadata: ParameterParserMetadata): ExtractStateM[Unit] =
      pure(())

    def fail[T](message: String): ExtractStateM[T] =
      failWith(CustomError(message))
  }
}
