package com.avast.cactus.grpc.client

import java.util.concurrent.Executor

import com.avast.cactus.v3._
import com.avast.cactus.{CactusFailures, Converter}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import com.google.protobuf.MessageLite
import io.grpc.{Status, StatusRuntimeException}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

object ClientCommonMethods {

  def executeRequest[ReqGpb <: MessageLite, RespGpb <: MessageLite: ClassTag, RespCaseClass: Converter[RespGpb, ?]](
      req: ReqGpb,
      f: ReqGpb => ListenableFuture[RespGpb],
      ex: Executor)(implicit ec: ExecutionContext): Future[ServerResponse[RespCaseClass]] = {
    f(req)
      .asScala(ex)
      .map(convertResponse[RespGpb, RespCaseClass])
      .recover {
        case e: StatusRuntimeException => Left(ServerError(e.getStatus, e.getTrailers))
      }
  }

  def convertResponse[RespGpb <: MessageLite: ClassTag, RespCaseClass: Converter[RespGpb, ?]](
      resp: RespGpb): ServerResponse[RespCaseClass] = {
    resp
      .asCaseClass[RespCaseClass]
      .badMap { errors =>
        ServerError(Status.INTERNAL.withDescription(formatCactusFailures("response", errors)))
      }
      .toEither
  }

  def formatCactusFailures(subject: String, errors: CactusFailures): String = {
    s"Errors when converting $subject: ${errors.mkString("[", ", ", "]")}"
  }

  private implicit class ListenableFuture2ScalaFuture[T](val f: ListenableFuture[T]) extends AnyVal {

    def asScala(implicit executor: Executor): Future[T] = {
      val p = Promise[T]()
      Futures.addCallback(f, new FutureCallback[T] {
        override def onSuccess(result: T): Unit = p.success(result)

        override def onFailure(t: Throwable): Unit = p.failure(t)
      }, executor)
      p.future
    }
  }

}
