package com.avast.cactus.grpc.client

import java.util.concurrent.Executor

import cats.effect.Async
import cats.syntax.all._
import com.avast.cactus.Converter
import com.avast.cactus.grpc.{CommonMethods, FromTask, ServerError, ServerResponse}
import com.avast.cactus.v3._
import com.google.common.util.concurrent._
import com.google.protobuf.MessageLite
import io.grpc._

import scala.concurrent._
import scala.language.higherKinds
import scala.reflect.ClassTag
object ClientCommonMethods extends CommonMethods {

  def executeRequest[F[_]: Async, ReqGpb <: MessageLite, RespGpb <: MessageLite: ClassTag, RespCaseClass: Converter[RespGpb, ?]](
      req: ReqGpb,
      ctx: Context,
      f: ReqGpb => ListenableFuture[RespGpb],
      ex: Executor)(implicit ec: ExecutionContext): F[ServerResponse[RespCaseClass]] = {

    Async[F]
      .async[RespGpb] { cb =>
        ctx
          .call[Future[RespGpb]](() => f(req).asScala(ex))
          .onComplete(r => cb(r.toEither))
      }
      .map(convertResponse[RespGpb, RespCaseClass])
      .recover {
        case e: StatusRuntimeException => Left(ServerError(e.getStatus, e.getTrailers))
      }
  }

  def convertResponse[RespGpb <: MessageLite: ClassTag, RespCaseClass: Converter[RespGpb, ?]](
      resp: RespGpb): ServerResponse[RespCaseClass] = {
    resp
      .asCaseClass[RespCaseClass]
      .leftMap { errors =>
        ServerError(Status.INTERNAL.withDescription(formatCactusFailures("response", errors)))
      }
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
