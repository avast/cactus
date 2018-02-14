package com.avast.cactus.grpc.server

import com.avast.cactus.Converter
import com.avast.cactus.grpc.CommonMethods
import com.avast.cactus.v3._
import com.google.protobuf.MessageLite
import io.grpc.stub.StreamObserver
import io.grpc.{Status, StatusException, StatusRuntimeException}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object ServerCommonMethods extends CommonMethods {
  def executeRequest[ReqCaseClass, RespCaseClass: ClassTag, RespGpb <: MessageLite: Converter[RespCaseClass, ?]](
      req: ReqCaseClass,
      f: ReqCaseClass => Future[Either[Status, RespCaseClass]])(implicit ec: ExecutionContext): Future[Either[StatusException, RespGpb]] = {
    f(req)
      .map {
        case Right(resp) => convertResponse[RespCaseClass, RespGpb](resp)
        case Left(status) => Left(new StatusException(status))
      }
  }

  def convertResponse[RespCaseClass: ClassTag, RespGpb <: MessageLite: Converter[RespCaseClass, ?]](
      resp: RespCaseClass): Either[StatusException, RespGpb] = {
    resp
      .asGpb[RespGpb]
      .badMap { errors =>
        new StatusException(Status.INTERNAL.withDescription(formatCactusFailures("response", errors)))
      }
      .toEither
  }

  def sendResponse[Resp <: MessageLite](respObs: StreamObserver[Resp]): PartialFunction[Try[Either[StatusException, Resp]], Unit] = {
    case Success(Right(resp)) =>
      respObs.onNext(resp)
      respObs.onCompleted()

    case Success(Left(statusException)) =>
      respObs.onError(statusException)

    case Failure(NonFatal(e)) =>
      respObs.onError(e)
  }

  def recoverWithStatus[Resp <: MessageLite]: PartialFunction[scala.Throwable, Either[StatusException, Resp]] = {
    case e: StatusException => Left(e)
    case e: StatusRuntimeException => Left(new StatusException(e.getStatus, e.getTrailers))
    case NonFatal(e) =>
      Left {
        new StatusException(Status.INTERNAL.withDescription(s"${e.getClass.getName}: ${e.getMessage}"))
      }
  }

  def withContext[Req, RespGpb, Ctx](createCtx: => Try[Ctx])(
      action: Ctx => Future[Either[StatusException, RespGpb]]): Future[Either[StatusException, RespGpb]] = {
    createCtx match {
      case scala.util.Success(ctx) =>
        action(ctx)

      case scala.util.Failure(NonFatal(e)) =>
        Future.successful {
          Left {
            new StatusException(
              Status.INVALID_ARGUMENT.withDescription("Unable to create context, maybe some headers are missing?").withCause(e))
          }

        }
    }
  }

}
