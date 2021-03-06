package com.avast.cactus.grpc.server

import cats.Monad
import cats.effect.{Effect, IO}
import cats.syntax.all._
import com.avast.cactus.Converter
import com.avast.cactus.grpc.CommonMethods
import com.avast.cactus.v3._
import com.google.protobuf.MessageLite
import io.grpc._
import io.grpc.stub.StreamObserver

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal

object ServerCommonMethods extends CommonMethods {
  def prepareCall[F[_]: Monad, ReqCaseClass, RespCaseClass, RespGpb](
      req: ReqCaseClass,
      call: ReqCaseClass => F[Either[Status, RespCaseClass]],
      handleResponse: RespCaseClass => Either[StatusException, RespGpb]): F[Either[StatusException, RespGpb]] = {

    call(req)
      .map {
        case Right(resp) => handleResponse(resp)
        case Left(status) => Left(new StatusException(status))
      }
  }

  def execute[F[_]: Effect, RespGpb <: MessageLite](respObs: StreamObserver[RespGpb])(action: F[Either[StatusException, RespGpb]])(
      implicit ec: ExecutionContext): Unit = {

    (IO.shift(ec) >> Effect[F].toIO(action)).recover(recoverWithStatus).unsafeRunAsync(sendResponse(respObs))
  }

  def convertResponse[RespCaseClass: ClassTag, RespGpb <: MessageLite: Converter[RespCaseClass, *]](
      resp: RespCaseClass): Either[StatusException, RespGpb] = {
    resp
      .asGpb[RespGpb]
      .leftMap { errors =>
        new StatusException(Status.INTERNAL.withDescription(formatCactusFailures("response", errors)))
      }
  }

  def sendResponse[Resp <: MessageLite](
      respObs: StreamObserver[Resp]): PartialFunction[Either[Throwable, Either[StatusException, Resp]], Unit] = {
    case Right(Right(resp)) =>
      respObs.onNext(resp)
      respObs.onCompleted()

    case Right(Left(statusException)) =>
      respObs.onError(statusException)

    case Left(NonFatal(e)) =>
      respObs.onError(e)
  }

  def recoverWithStatus[Resp <: MessageLite]: PartialFunction[scala.Throwable, Either[StatusException, Resp]] = {
    case e: StatusException => Left(e)
    case e: StatusRuntimeException => Left(new StatusException(e.getStatus, e.getTrailers))
    case NonFatal(e) =>
      Left {
        new StatusException(Status.INTERNAL.withDescription(s"${e.getClass.getName}: ${e.getMessage}").withCause(e))
      }
  }

  def withContext[F[_]: Monad, Req, RespGpb, Ctx](createCtx: => Try[Ctx])(
      action: Ctx => F[Either[StatusException, RespGpb]]): F[Either[StatusException, RespGpb]] = {
    createCtx match {
      case scala.util.Success(ctx) =>
        action(ctx)

      case scala.util.Failure(NonFatal(e)) =>
        Monad[F].pure {
          Left {
            new StatusException(
              Status.INVALID_ARGUMENT.withDescription("Unable to create context, maybe some headers are missing?").withCause(e))
          }
        }
    }
  }

}
