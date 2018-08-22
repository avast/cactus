package com.avast.cactus.grpc.server

import cats.effect.{Effect, IO}
import cats.syntax.all._
import com.avast.cactus.Converter
import com.avast.cactus.grpc.{CommonMethods, ToTask}
import com.avast.cactus.v3._
import com.google.protobuf.MessageLite
import io.grpc._
import io.grpc.stub.StreamObserver

import scala.concurrent._
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object ServerCommonMethods extends CommonMethods {
  def executeRequest[F[_]: Effect, ReqCaseClass, RespCaseClass, RespGpb](req: ReqCaseClass,
                                                                         call: ReqCaseClass => F[Either[Status, RespCaseClass]],
                                                                         handleResponse: RespCaseClass => Either[StatusException, RespGpb])(
      implicit ec: ExecutionContext): Future[Either[StatusException, RespGpb]] = {

    val p = Promise[Either[StatusException, RespGpb]]

    (IO.shift >> Effect[F].runAsync(call(req)) {
      case Right(Right(resp)) => IO { p.complete(Success(handleResponse(resp))) }
      case Right(Left(status)) => IO { p.complete(Success(Left(new StatusException(status)))) }
      case Left(err) => IO { p.complete(Failure(err)) }
    }).unsafeRunSync()

    p.future
  }

  def convertResponse[RespCaseClass: ClassTag, RespGpb <: MessageLite: Converter[RespCaseClass, ?]](
      resp: RespCaseClass): Either[StatusException, RespGpb] = {
    resp
      .asGpb[RespGpb]
      .leftMap { errors =>
        new StatusException(Status.INTERNAL.withDescription(formatCactusFailures("response", errors)))
      }
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
