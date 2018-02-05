package com.avast.cactus.grpc.server

import java.util.concurrent.Callable

import cats.data.EitherT
import cats.implicits._
import com.avast.cactus.grpc.GrpcMetadata
import io.grpc.{Context, Metadata, Status, StatusException}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ServerInterceptorsWrapper(interceptors: immutable.Seq[ServerAsyncInterceptor])(implicit ec: ExecutionContext) {
  def withInterceptors[Resp](serverCall: => Future[Either[StatusException, Resp]]): Future[Either[StatusException, Resp]] = {
    try {
      val resolvedInterceptors = {
        val s = EitherT[Future, StatusException, GrpcMetadata] {
          Future.successful(Right(GrpcMetadata(Context.current(), new Metadata())))
        }

        interceptors
          .foldLeft(s) { case (prev, int) => prev.flatMap(r => EitherT(int(r)).leftMap(new StatusException(_))) }
          .value
      }

      resolvedInterceptors
        .flatMap {
          case Right(GrpcMetadata(ctx, _)) =>
            ctx
              .call(new Callable[Future[Either[StatusException, Resp]]] {
                override def call(): Future[Either[StatusException, Resp]] = {
                  serverCall
                }
              })

          case Left(statusException) => Future.failed(statusException)
        }
        .recover(ServerCommonMethods.recoverWithStatus)
    } catch {
      case NonFatal(e) =>
        Future.failed {
          new StatusException(Status.INTERNAL.withCause(e).withDescription("Request could not been processed"))
        }
    }
  }
}
